package com.zendo.shared.outbox;

import java.time.Instant;
import java.util.UUID;
import com.zendo.shared.messaging.DomainEvent;

/**
 * Represents a serialized domain event stored in the outbox table.
 */
public record OutboxEvent(
    UUID id,
    String aggregateType,
    String aggregateId,
    String eventType,
    String payload,
    boolean processed,
    Instant createdAt
) {
    public static OutboxEvent from(DomainEvent domainEvent, String serializedPayload, String aggregateType) {
        return new OutboxEvent(
            domainEvent.getEventId(),
            aggregateType,
            domainEvent.getAggregateId(),
            domainEvent.getEventType(),
            serializedPayload,
            false,
            domainEvent.getOccurredOn()
        );
    }
}
