package com.zendo.order.domain.events;

import com.zendo.shared.messaging.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderPlaced(
        UUID eventId,
        Instant occurredAt,
        String orderId,
        String customerId,
        String cartId,
        BigDecimal totalAmount,
        String currency
) implements DomainEvent {
    @Override
    public UUID getEventId() { return eventId; }
    
    @Override
    public Instant getOccurredOn() { return occurredAt; }
    
    @Override
    public String getAggregateId() { return orderId; }
    
    @Override
    public String getEventType() { return "OrderPlaced"; }
}
