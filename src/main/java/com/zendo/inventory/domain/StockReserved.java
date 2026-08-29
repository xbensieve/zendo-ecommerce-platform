package com.zendo.inventory.domain;

import com.zendo.shared.messaging.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record StockReserved(
        UUID eventId,
        Instant occurredOn,
        String productId,
        int quantity,
        String referenceId
) implements DomainEvent {
    @Override public UUID getEventId() { return eventId; }
    @Override public Instant getOccurredOn() { return occurredOn; }
    @Override public String getAggregateId() { return productId; }
    @Override public String getEventType() { return "StockReserved"; }
}
