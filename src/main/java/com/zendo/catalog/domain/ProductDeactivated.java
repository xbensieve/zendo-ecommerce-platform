package com.zendo.catalog.domain;

import com.zendo.shared.messaging.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record ProductDeactivated(UUID eventId, Instant occurredOn, String productId) implements DomainEvent {
    @Override
    public UUID getEventId() { return eventId; }
    @Override
    public Instant getOccurredOn() { return occurredOn; }
    @Override
    public String getAggregateId() { return productId; }
    @Override
    public String getEventType() { return "ProductDeactivated"; }
}
