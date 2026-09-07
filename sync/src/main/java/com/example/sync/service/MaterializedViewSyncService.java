package com.example.sync.service;

import com.example.sync.cdc.MaterializedViewHandler;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps customer_order_summary in sync by reacting to changes on
 * customers, orders, and order_items. Given an affected customer_id,
 * recomputes just that one row and upserts it.
 */
@Service
public class MaterializedViewSyncService implements MaterializedViewHandler {

    private static final Logger log = LoggerFactory.getLogger(MaterializedViewSyncService.class);
    private static final Set<String> WATCHED_TABLES = Set.of("customers", "orders", "order_items");

    private final JdbcTemplate jdbcTemplate;

    public MaterializedViewSyncService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Set<String> watchedTables() {
        return WATCHED_TABLES;
    }

    @Override
    public void handle(String table, String op, JsonNode before, JsonNode after) {
        JsonNode row = "d".equals(op) ? before : after;
        if (row == null || row.isNull()) {
            return;
        }

        Long customerId = switch (table) {
            case "customers" -> row.path("id").isMissingNode() ? null : row.path("id").asLong();
            case "orders" -> row.path("customer_id").isMissingNode() ? null : row.path("customer_id").asLong();
            case "order_items" -> {
                Long orderId = row.path("order_id").isMissingNode() ? null : row.path("order_id").asLong();
                yield resolveCustomerIdFromOrderId(orderId);
            }
            default -> null;
        };

        if (customerId != null) {
            refreshCustomerSummary(customerId);
        }
    }

    private void refreshCustomerSummary(Long customerId) {
        String aggregateSql =
                "SELECT c.id AS customer_id, c.name AS customer_name, " +
                        "       COUNT(DISTINCT o.id) AS total_orders, " +
                        "       COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS total_spent, " +
                        "       MAX(o.created_at) AS last_order_at " +
                        "FROM customers c " +
                        "LEFT JOIN orders o ON o.customer_id = c.id " +
                        "LEFT JOIN order_items oi ON oi.order_id = o.id " +
                        "WHERE c.id = ? " +
                        "GROUP BY c.id, c.name";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(aggregateSql, customerId);

        if (rows.isEmpty()) {
            jdbcTemplate.update("DELETE FROM customer_order_summary WHERE customer_id = ?", customerId);
            log.info("Removed customer_order_summary row for customer_id={}", customerId);
            return;
        }

        Map<String, Object> row = rows.get(0);
        String upsertSql =
                "INSERT INTO customer_order_summary " +
                        "   (customer_id, customer_name, total_orders, total_spent, last_order_at) " +
                        "VALUES (?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "   customer_name = VALUES(customer_name), " +
                        "   total_orders = VALUES(total_orders), " +
                        "   total_spent = VALUES(total_spent), " +
                        "   last_order_at = VALUES(last_order_at)";

        jdbcTemplate.update(upsertSql,
                row.get("customer_id"),
                row.get("customer_name"),
                row.get("total_orders"),
                row.get("total_spent"),
                row.get("last_order_at"));

        log.info("Refreshed customer_order_summary for customer_id={}", customerId);
    }

    private Long resolveCustomerIdFromOrderId(Long orderId) {
        if (orderId == null) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT customer_id FROM orders WHERE id = ?", Long.class, orderId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }
}