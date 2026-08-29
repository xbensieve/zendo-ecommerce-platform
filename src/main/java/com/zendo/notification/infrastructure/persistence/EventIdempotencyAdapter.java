package com.zendo.notification.infrastructure.persistence;

import com.zendo.notification.application.ports.EventIdempotencyPort;
import org.springframework.stereotype.Component;

@Component
public class EventIdempotencyAdapter implements EventIdempotencyPort {

    private final ProcessedEventRepository repository;

    public EventIdempotencyAdapter(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean hasBeenProcessed(String eventId) {
        return repository.existsById(eventId);
    }

    @Override
    public void markProcessed(String eventId) {
        repository.save(new ProcessedEventJpaEntity(eventId));
    }
}
