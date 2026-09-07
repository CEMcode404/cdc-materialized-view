package com.example.sync.cdc;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

/**
 * Implemented by each materialized-view sync service. Debezium's listener
 * dispatches every change event to whichever handlers declare interest in
 * that table - so adding a new materialized view is just adding a new
 * handler bean, no new Debezium engine or deployable required.
 */
public interface MaterializedViewHandler {

    /**
     * The set of table names (unqualified, e.g. "orders") this handler
     * needs to react to. DebeziumConfig unions these across all registered
     * handlers to build table.include.list.
     */
    Set<String> watchedTables();

    /**
     * Called once per row-level change event for any table in watchedTables().
     * @param table  the table name the event came from
     * @param op     "c" (create), "u" (update), "d" (delete), "r" (read/snapshot)
     * @param before row state before the change (null for inserts/snapshots)
     * @param after  row state after the change (null for deletes)
     */
    void handle(String table, String op, JsonNode before, JsonNode after);
}