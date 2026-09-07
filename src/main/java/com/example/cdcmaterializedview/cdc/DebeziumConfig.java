package com.example.cdcmaterializedview.cdc;

import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Boots Debezium's EMBEDDED engine - Debezium reads the MySQL binlog the
 * normal way, but instead of publishing to a Kafka topic, it hands each
 * change event directly to our callback method, inside this same JVM.
 * No Kafka, no Kafka Connect cluster required.
 */
@Component
public class DebeziumConfig implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DebeziumConfig.class);
    private final DebeziumChangeEventListener changeEventListener;

    @Value("${debezium.db.hostname}")
    private String dbHostname;
    @Value("${debezium.db.port}")
    private String dbPort;
    @Value("${debezium.db.user}")
    private String dbUser;
    @Value("${debezium.db.password}")
    private String dbPassword;
    @Value("${debezium.db.name}")
    private String dbName;
    @Value("${debezium.offset.dir}")
    private String offsetDir;

    private ExecutorService executor;
    private DebeziumEngine<io.debezium.engine.ChangeEvent<String, String>> engine;
    private volatile boolean running = false;

    public DebeziumConfig(DebeziumChangeEventListener changeEventListener) {
        this.changeEventListener = changeEventListener;
    }

    private Properties buildProperties() {
        File dir = new File(offsetDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        Properties props = new Properties();
        props.setProperty("name", "cdcdemo-embedded-engine");
        props.setProperty("connector.class", "io.debezium.connector.mysql.MySqlConnector");

        props.setProperty("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore");
        props.setProperty("offset.storage.file.filename", new File(dir, "offsets.dat").getAbsolutePath());
        props.setProperty("offset.flush.interval.ms", "1000");

        // Debezium 2.x uses "schema.history.internal", not the older "database.history"
        props.setProperty("schema.history.internal", "io.debezium.storage.file.history.FileSchemaHistory");
        props.setProperty("schema.history.internal.file.filename", new File(dir, "schema-history.dat").getAbsolutePath());

        props.setProperty("database.hostname", dbHostname);
        props.setProperty("database.port", dbPort);
        props.setProperty("database.user", dbUser);
        props.setProperty("database.password", dbPassword);
        props.setProperty("database.server.id", "223344");

        // Debezium 2.x uses "topic.prefix" instead of the older "database.server.name"
        props.setProperty("topic.prefix", "cdcdemo-server");

        props.setProperty("database.include.list", dbName);
        props.setProperty("table.include.list",
                dbName + ".customers," + dbName + ".orders," + dbName + ".order_items");

        props.setProperty("include.schema.changes", "false");
        props.setProperty("snapshot.mode", "initial");

        props.setProperty("key.converter", "org.apache.kafka.connect.json.JsonConverter");
        props.setProperty("key.converter.schemas.enable", "false");
        props.setProperty("value.converter", "org.apache.kafka.connect.json.JsonConverter");
        props.setProperty("value.converter.schemas.enable", "false");
        props.setProperty("bootstrap.servers", "localhost:9092");
        props.setProperty("decimal.handling.mode", "string");

        return props;
    }

    @Override
    public void start() {
        Properties props = buildProperties();

        engine = DebeziumEngine.create(Json.class)
                .using(props)
                .notifying(changeEventListener::handle)
                .using((success, message, error) -> {
                    if (!success) {
                        log.error("Debezium engine stopped with error: {}", message, error);
                    }
                })
                .build();

        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "debezium-embedded-engine");
            t.setDaemon(true);
            return t;
        });
        executor.execute(engine);
        running = true;
        log.info("Debezium embedded engine started (watching {}.customers/orders/order_items)", dbName);
    }

    @Override
    public void stop() {
        try {
            if (engine != null) {
                engine.close();
            }
        } catch (Exception e) {
            log.warn("Error closing Debezium engine", e);
        } finally {
            if (executor != null) {
                executor.shutdownNow();
            }
            running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 1;
    }
}