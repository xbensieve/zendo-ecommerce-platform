package com.zendo.shared.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Base contract for all Domain Events in the system.
 */
public interface DomainEvent {
    UUID getEventId();
    Instant getOccurredOn();
    String getAggregateId();
    String getEventType();
}
