package com.zendo.shared.outbox;

import com.zendo.shared.messaging.DomainEvent;
import com.zendo.shared.messaging.EventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxEventPublisher implements EventPublisher {

    private final OutboxEventStore outboxEventStore;

    public OutboxEventPublisher(OutboxEventStore outboxEventStore) {
        this.outboxEventStore = outboxEventStore;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(DomainEvent event) {
        // We require an existing transaction so the outbox event commits with the business state
        // We will default the aggregateType to the class name of the event or generic
        // In a real system, we'd map event classes to aggregate types explicitly.
        OutboxEvent outboxEvent = OutboxEvent.from(event, "{}", "Vendor"); // Mock serialization
        outboxEventStore.save(outboxEvent);
    }
}
