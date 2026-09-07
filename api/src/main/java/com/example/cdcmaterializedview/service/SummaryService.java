package com.example.cdcmaterializedview.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SummaryService {

    private final JdbcTemplate jdbcTemplate;

    public SummaryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * The "old way": joins customers -> orders -> order_items and aggregates
     * live, on every single request. This is the query pattern that gets
     * slow as order/order_items grow - kept around so we can later compare
     * it against reading from the materialized view.
     */
    public Map<String, Object> getSummarySlow(Long customerId) {
        String sql =
                "SELECT c.id AS customer_id, c.name AS customer_name, " +
                        "       COUNT(DISTINCT o.id) AS total_orders, " +
                        "       COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS total_spent, " +
                        "       MAX(o.created_at) AS last_order_at " +
                        "FROM customers c " +
                        "LEFT JOIN orders o ON o.customer_id = c.id " +
                        "LEFT JOIN order_items oi ON oi.order_id = o.id " +
                        "WHERE c.id = ? " +
                        "GROUP BY c.id, c.name";
        return jdbcTemplate.queryForMap(sql, customerId);
    }
}