package com.zendo.shared.messaging;

/**
 * Abstraction for consumers that must process messages idempotently.
 */
public interface IdempotentEventConsumer<T extends DomainEvent> {
    
    /**
     * Consumes the event safely, ensuring that duplicate deliveries
     * do not compromise system state.
     */
    void consume(T event);
    
    /**
     * Checks if the event has already been processed (e.g., via Redis or a DB table).
     */
    boolean isAlreadyProcessed(String eventId);
}
