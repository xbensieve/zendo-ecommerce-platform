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
        // Determine the schema based on aggregateType. For now, simple routing.
        String schema = "shared";
        if ("Vendor".equalsIgnoreCase(outboxEvent.aggregateType())) schema = "vendor";
        if ("Product".equalsIgnoreCase(outboxEvent.aggregateType())) schema = "catalog";
        
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
