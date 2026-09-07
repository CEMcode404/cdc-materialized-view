# CDC-Backed Materialized View (MySQL + Java, no Kafka)

MySQL has no built-in `MATERIALIZED VIEW`. This project demonstrates replacing
a slow, on-demand join+aggregation query with a small denormalized table that
stays in sync automatically via **Change Data Capture (CDC)** by reading
MySQL's binlog directly with Debezium's **embedded engine**, with no Kafka
or Kafka Connect required.

## Why CDC instead of polling?

A common workaround for missing materialized views is a scheduled job that
periodically recomputes and swaps a summary table. That approach has two
real costs: a fixed staleness window, and it tends to redo more work than
necessary since it doesn't know exactly what changed.

CDC instead reacts to the MySQL binlog directly, one event per **row**
change, not per table and not per statement, so only the specific affected
row gets recomputed, as soon as the change happens.

## Architecture

    ┌─────────┐  writes   ┌────────┐  binlog   ┌──────────────────┐
    │   api   │ ────────> │ MySQL  │ ────────> │       sync        │
    │ (CRUD)  │           │        │           │ (Debezium engine)  │
    └─────────┘           └────────┘           └──────────────────┘
                                ▲                        │
                                └──── upserts ───────────┘

- **`api/`** — Spring Boot CRUD app for `customers`, `orders`, `order_items`.
  No knowledge of CDC at all. Can be scaled to multiple instances freely.
- **`sync/`** — a separate, standalone Spring Boot app. Runs Debezium's
  embedded engine, reads the binlog, and keeps `customer_order_summary` in
  sync. Deliberately decoupled from `api` so it stays a **single instance**
  even if `api` is scaled out by avoiding duplicate CDC processing.
- Both connect to the same MySQL database and are otherwise independent
  processes/deployables.

## Extending it

Adding a new materialized view doesn't require touching the CDC plumbing.
`sync` dispatches every change event to any bean implementing:

```java
public interface MaterializedViewHandler {
    Set<String> watchedTables();
    void handle(String table, String op, JsonNode before, JsonNode after);
}
```

Add a new `@Service` implementing this interface, and `sync`'s Debezium
config automatically expands `table.include.list` to include whatever
tables the new handler declares. No changes needed to `DebeziumConfig` or
`DebeziumChangeEventListener`.

## Running it

```bash
docker-compose up -d          # starts MySQL with binlog enabled
```

Then run `api` and `sync` as two separate Spring Boot apps (e.g. from
IntelliJ, or `./mvnw spring-boot:run` in each folder). Both need MySQL
running first.