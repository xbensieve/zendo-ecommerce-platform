package com.zendo.notification.application.ports;

/**
 * Port for checking event idempotency.
 */
public interface EventIdempotencyPort {

    /**
     * Checks if the given event ID has already been processed.
     */
    boolean hasBeenProcessed(String eventId);

    /**
     * Marks the given event ID as processed.
     */
    void markProcessed(String eventId);
}
