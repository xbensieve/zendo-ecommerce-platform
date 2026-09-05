package com.zendo.shared.outbox;

import com.zendo.shared.messaging.DomainEvent;
import com.zendo.shared.messaging.EventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxEventPublisher implements EventPublisher {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final OutboxEventStore outboxEventStore;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public OutboxEventPublisher(OutboxEventStore outboxEventStore, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.outboxEventStore = outboxEventStore;
        this.objectMapper = objectMapper;
    }

    public OutboxEventPublisher(OutboxEventStore outboxEventStore) {
        this(outboxEventStore, new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(DomainEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Failed to serialize domain event: {}", event, e);
            payload = "{}";
        }

        String aggregateType = resolveAggregateType(event);
        OutboxEvent outboxEvent = OutboxEvent.from(event, payload, aggregateType);
        outboxEventStore.save(outboxEvent);
    }

    private String resolveAggregateType(DomainEvent event) {
        if (event == null) return "Unknown";
        String className = event.getClass().getSimpleName();
        if (className.startsWith("Vendor")) return "Vendor";
        if (className.startsWith("Product")) return "Product";
        if (className.startsWith("Order")) return "Order";
        if (className.startsWith("Payment")) return "Payment";
        if (className.startsWith("Stock") || className.startsWith("Inventory")) return "Inventory";
        return className;
    }
}
