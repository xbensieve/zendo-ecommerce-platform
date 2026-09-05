package com.zendo.shared.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.zendo.shared.messaging.EventSigner;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private final JdbcClient jdbcClient;
    private final RabbitTemplate rabbitTemplate;
    private final MeterRegistry meterRegistry;
    private final EventSigner eventSigner;

    private final PlatformTransactionManager transactionManager;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    public OutboxRelay(
            JdbcClient jdbcClient, 
            RabbitTemplate rabbitTemplate, 
            MeterRegistry meterRegistry, 
            EventSigner eventSigner,
            @org.springframework.beans.factory.annotation.Autowired(required = false) PlatformTransactionManager transactionManager) {
        this.jdbcClient = jdbcClient;
        this.rabbitTemplate = rabbitTemplate;
        this.meterRegistry = meterRegistry;
        this.eventSigner = eventSigner;
        this.transactionManager = transactionManager;
        this.transactionTemplate = transactionManager != null ? new org.springframework.transaction.support.TransactionTemplate(transactionManager) : null;
        
        // Register gauge for pending events
        meterRegistry.gauge("outbox_pending_count", Tags.of("schema", "order_ctx"), this, relay -> relay.countPending("order_ctx"));
        meterRegistry.gauge("outbox_pending_count", Tags.of("schema", "payment"), this, relay -> relay.countPending("payment"));
    }

    private static final java.util.Set<String> ALLOWED_SCHEMAS = java.util.Set.of(
            "order_ctx", "payment", "identity", "vendor", "catalog", "inventory", "promotion", "review"
    );

    public static String validateSchema(String schema) {
        if (schema == null || !ALLOWED_SCHEMAS.contains(schema.trim().toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("Unauthorized or invalid schema identifier: " + schema);
        }
        return schema.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public OutboxRelay(JdbcClient jdbcClient, RabbitTemplate rabbitTemplate, MeterRegistry meterRegistry, EventSigner eventSigner) {
        this(jdbcClient, rabbitTemplate, meterRegistry, eventSigner, null);
    }

    private int countPending(String schema) {
        try {
            String validSchema = validateSchema(schema);
            return jdbcClient.sql("SELECT COUNT(*) FROM " + validSchema + ".outbox_events WHERE processed = FALSE")
                    .query(Integer.class).single();
        } catch (Exception e) {
            return 0;
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void relayEvents() {
        relayFromSchema("order_ctx", "order.events");
        relayFromSchema("payment", "payment.events");
    }

    public void relayFromSchema(String schema, String routingKey) {
        try {
            if (transactionTemplate != null) {
                transactionTemplate.execute(status -> {
                    processBatch(schema, routingKey);
                    return null;
                });
            } else {
                processBatch(schema, routingKey);
            }
        } catch (Exception e) {
            meterRegistry.counter("outbox_publish_failed_total", "schema", schema).increment();
            log.error("Failed to relay outbox events from schema {}", schema, e);
        }
    }

    private void processBatch(String schema, String routingKey) {
        String validSchema = validateSchema(schema);
        // Find unpublished events with FOR UPDATE SKIP LOCKED to prevent multi-instance race conditions
        String selectSql = "SELECT id, payload FROM " + validSchema + ".outbox_events WHERE processed = FALSE ORDER BY created_at ASC LIMIT 50 FOR UPDATE SKIP LOCKED";
        
        List<Map<String, Object>> events = jdbcClient.sql(selectSql).query().listOfRows();

        for (Map<String, Object> event : events) {
            UUID id = (UUID) event.get("id");
            String payload = event.get("payload").toString();
            String signature = eventSigner.sign(payload);

            // Publish with signature header for event authenticity
            rabbitTemplate.convertAndSend("zendo.topic", routingKey, payload, message -> {
                message.getMessageProperties().setHeader("X-Event-Signature", signature);
                return message;
            });

            // Mark as published
            String updateSql = "UPDATE " + validSchema + ".outbox_events SET processed = TRUE WHERE id = :id";
            jdbcClient.sql(updateSql).param("id", id).update();
            
            meterRegistry.counter("outbox_published_total", "schema", validSchema).increment();
            log.info("Relayed outbox event {} from schema {} to routing key {}", id, validSchema, routingKey);
        }
    }
}
