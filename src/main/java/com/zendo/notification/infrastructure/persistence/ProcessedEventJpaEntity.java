package com.zendo.notification.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity for tracking processed event IDs (idempotency).
 */
@Entity
@Table(name = "processed_events", schema = "notification")
public class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id", length = 255)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEventJpaEntity() {}

    public ProcessedEventJpaEntity(String eventId) {
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }

    public String getEventId() { return eventId; }
    public Instant getProcessedAt() { return processedAt; }
}
