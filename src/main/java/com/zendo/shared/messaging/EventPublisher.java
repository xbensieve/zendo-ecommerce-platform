package com.zendo.shared.messaging;

/**
 * Contract for publishing events to the messaging infrastructure (e.g., RabbitMQ).
 */
public interface EventPublisher {
    
    /**
     * Publishes a domain event directly to the messaging broker.
     * Note: In a transactional context, events should generally be written 
     * to the Outbox instead of using this directly, unless this implementation 
     * handles the Outbox internally.
     */
    void publish(DomainEvent event);
}
