package com.zendo.payment.domain.events;

import com.zendo.shared.messaging.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentAuthorized(
        UUID eventId,
        Instant occurredAt,
        String paymentId,
        String orderId,
        String customerId,
        BigDecimal amount,
        String currency
) implements DomainEvent {
    @Override
    public UUID getEventId() { return eventId; }
    
    @Override
    public Instant getOccurredOn() { return occurredAt; }
    
    @Override
    public String getAggregateId() { return paymentId; }
    
    @Override
    public String getEventType() { return "PaymentAuthorized"; }
}
