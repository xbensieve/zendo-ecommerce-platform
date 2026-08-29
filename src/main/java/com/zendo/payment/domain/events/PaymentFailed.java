package com.zendo.payment.domain.events;

import com.zendo.shared.messaging.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record PaymentFailed(
        UUID eventId,
        Instant occurredAt,
        String paymentId,
        String orderId,
        String customerId,
        String reason
) implements DomainEvent {
    @Override
    public UUID getEventId() { return eventId; }
    
    @Override
    public Instant getOccurredOn() { return occurredAt; }
    
    @Override
    public String getAggregateId() { return paymentId; }
    
    @Override
    public String getEventType() { return "PaymentFailed"; }
}
