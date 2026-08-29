package com.zendo.shared.outbox;

/**
 * Contract for persisting outbox events within the same transaction 
 * as the business state changes.
 */
public interface OutboxEventStore {
    
    /**
     * Saves an event to the module's outbox table.
     */
    void save(OutboxEvent outboxEvent);
}
