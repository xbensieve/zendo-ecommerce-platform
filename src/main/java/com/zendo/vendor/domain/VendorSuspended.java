package com.zendo.vendor.domain;

import com.zendo.shared.messaging.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record VendorSuspended(UUID eventId, Instant occurredOn, String vendorId) implements DomainEvent {
    @Override
    public UUID getEventId() { return eventId; }
    @Override
    public Instant getOccurredOn() { return occurredOn; }
    @Override
    public String getAggregateId() { return vendorId; }
    @Override
    public String getEventType() { return "VendorSuspended"; }
}
