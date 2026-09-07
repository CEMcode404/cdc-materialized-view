package com.example.cdcmaterializedview.cdc;

import com.example.cdcmaterializedview.service.MaterializedViewSyncService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.debezium.engine.ChangeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Receives every row-level change event from Debezium (one call per changed
 * row, not per SQL statement). For each event, figures out which customer_id
 * it affects, then asks MaterializedViewSyncService to recompute that one row.
 */
@Component
public class DebeziumChangeEventListener {

    private static final Logger log = LoggerFactory.getLogger(DebeziumChangeEventListener.class);

    private final MaterializedViewSyncService syncService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DebeziumChangeEventListener(MaterializedViewSyncService syncService) {
        this.syncService = syncService;
    }

    public void handle(ChangeEvent<String, String> event) {
        String valueJson = event.value();
        if (valueJson == null) {
            return; // tombstone event, nothing to do without Kafka log compaction
        }

        try {
            JsonNode payload = objectMapper.readTree(valueJson);

            String op = payload.path("op").asText(null);
            if (op == null) {
                return;
            }

            String sourceTable = payload.path("source").path("table").asText(null);
            JsonNode after = payload.get("after");
            JsonNode before = payload.get("before");

            Long customerId = resolveAffectedCustomerId(sourceTable, op, after, before);
            if (customerId != null) {
                syncService.refreshCustomerSummary(customerId);
            }
        } catch (Exception e) {
            log.error("Failed to process CDC event: {}", valueJson, e);
        }
    }

    private Long resolveAffectedCustomerId(String table, String op, JsonNode after, JsonNode before) {
        if (table == null) {
            return null;
        }

        // Deletes only have "before"; inserts/updates use "after"
        JsonNode row = "d".equals(op) ? before : after;
        if (row == null || row.isNull()) {
            return null;
        }

        switch (table) {
            case "customers":
                return row.path("id").isMissingNode() ? null : row.path("id").asLong();

            case "orders":
                return row.path("customer_id").isMissingNode() ? null : row.path("customer_id").asLong();

            case "order_items":
                Long orderId = row.path("order_id").isMissingNode() ? null : row.path("order_id").asLong();
                return syncService.resolveCustomerIdFromOrderId(orderId);

            default:
                return null;
        }
    }
}