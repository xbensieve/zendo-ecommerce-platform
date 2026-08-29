package com.zendo.shared.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private final JdbcClient jdbcClient;
    private final RabbitTemplate rabbitTemplate;
    private final MeterRegistry meterRegistry;

    public OutboxRelay(JdbcClient jdbcClient, RabbitTemplate rabbitTemplate, MeterRegistry meterRegistry) {
        this.jdbcClient = jdbcClient;
        this.rabbitTemplate = rabbitTemplate;
        this.meterRegistry = meterRegistry;
        
        // Register gauge for pending events
        meterRegistry.gauge("outbox_pending_count", Tags.of("schema", "order_ctx"), this, relay -> relay.countPending("order_ctx"));
        meterRegistry.gauge("outbox_pending_count", Tags.of("schema", "payment"), this, relay -> relay.countPending("payment"));
    }

    private int countPending(String schema) {
        try {
            return jdbcClient.sql("SELECT COUNT(*) FROM " + schema + ".outbox_events WHERE processed = FALSE")
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

    private void relayFromSchema(String schema, String routingKey) {
        try {
            // Find unpublished events
            String selectSql = "SELECT id, payload FROM " + schema + ".outbox_events WHERE processed = FALSE ORDER BY created_at ASC LIMIT 50";
            
            List<Map<String, Object>> events = jdbcClient.sql(selectSql).query().listOfRows();

            for (Map<String, Object> event : events) {
                UUID id = (UUID) event.get("id");
                String payload = event.get("payload").toString();

                // Publish
                rabbitTemplate.convertAndSend("zendo.topic", routingKey, payload);

                // Mark as published
                String updateSql = "UPDATE " + schema + ".outbox_events SET processed = TRUE WHERE id = :id";
                jdbcClient.sql(updateSql).param("id", id).update();
                
                meterRegistry.counter("outbox_published_total", "schema", schema).increment();
                log.info("Relayed outbox event {} from schema {} to routing key {}", id, schema, routingKey);
            }
        } catch (Exception e) {
            meterRegistry.counter("outbox_publish_failed_total", "schema", schema).increment();
            log.error("Failed to relay outbox events from schema {}", schema, e);
        }
    }
}
