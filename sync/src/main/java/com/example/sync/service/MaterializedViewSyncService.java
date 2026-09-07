package com.example.sync.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Given a customer_id whose data changed, recompute just that customer's
 * summary row and upsert it. Never recomputes the whole view - only the
 * slice affected by the CDC event that triggered it.
 */
@Service
public class MaterializedViewSyncService {

    private static final Logger log = LoggerFactory.getLogger(MaterializedViewSyncService.class);

    private final JdbcTemplate jdbcTemplate;

    public MaterializedViewSyncService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void refreshCustomerSummary(Long customerId) {
        if (customerId == null) {
            return;
        }

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
            // Customer was deleted - remove it from the view too
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

    /**
     * order_items rows only carry order_id, not customer_id - resolve it
     * by looking up the still-normalized orders table.
     */
    public Long resolveCustomerIdFromOrderId(Long orderId) {
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