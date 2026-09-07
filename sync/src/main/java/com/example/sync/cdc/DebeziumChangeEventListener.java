package com.example.sync.cdc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.debezium.engine.ChangeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Receives every row-level change event from Debezium (one call per changed
 * row, not per SQL statement). Parses the event, then dispatches it to
 * every registered MaterializedViewHandler that declared interest in the
 * source table - adding a new materialized view is just adding a new
 * handler bean, this class doesn't need to change.
 */
@Component
public class DebeziumChangeEventListener {

    private static final Logger log = LoggerFactory.getLogger(DebeziumChangeEventListener.class);

    private final List<MaterializedViewHandler> handlers;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DebeziumChangeEventListener(List<MaterializedViewHandler> handlers) {
        this.handlers = handlers;
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
            if (sourceTable == null) {
                return;
            }

            JsonNode after = payload.get("after");
            JsonNode before = payload.get("before");

            for (MaterializedViewHandler handler : handlers) {
                if (handler.watchedTables().contains(sourceTable)) {
                    handler.handle(sourceTable, op, before, after);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process CDC event: {}", valueJson, e);
        }
    }
}