package com.zendo.shared.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventStoreImpl implements OutboxEventStore {

    private final JdbcTemplate jdbcTemplate;

    public OutboxEventStoreImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(OutboxEvent outboxEvent) {
        // Determine the schema based on aggregateType
        String schema = "order_ctx";
        String aggType = outboxEvent.aggregateType() != null ? outboxEvent.aggregateType() : "";
        String evtType = outboxEvent.eventType() != null ? outboxEvent.eventType() : "";

        if ("Vendor".equalsIgnoreCase(aggType)) {
            schema = "vendor";
        } else if ("Product".equalsIgnoreCase(aggType)) {
            schema = "catalog";
        } else if ("Inventory".equalsIgnoreCase(aggType) || evtType.contains("Stock")) {
            schema = "inventory";
        } else if ("Payment".equalsIgnoreCase(aggType)) {
            schema = "payment";
        } else if ("Identity".equalsIgnoreCase(aggType) || "User".equalsIgnoreCase(aggType)) {
            schema = "identity";
        } else if ("Review".equalsIgnoreCase(aggType)) {
            schema = "review";
        } else if ("Promotion".equalsIgnoreCase(aggType) || "FlashSale".equalsIgnoreCase(aggType)) {
            schema = "promotion";
        }
        
        String sql = String.format(
            "INSERT INTO %s.outbox_events (id, aggregate_type, aggregate_id, event_type, payload, processed, created_at) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)", 
            schema
        );
        
        jdbcTemplate.update(sql,
            outboxEvent.id(),
            outboxEvent.aggregateType(),
            outboxEvent.aggregateId(),
            outboxEvent.eventType(),
            outboxEvent.payload(),
            outboxEvent.processed(),
            java.sql.Timestamp.from(outboxEvent.createdAt())
        );
    }
}
