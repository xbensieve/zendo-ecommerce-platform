package com.zendo.notification.application;

import com.zendo.notification.application.ports.EventIdempotencyPort;
import com.zendo.notification.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application service for notification operations.
 * Handles creation with idempotency checks and status transitions.
 */
@Service
public class NotificationUseCases {

    private static final Logger log = LoggerFactory.getLogger(NotificationUseCases.class);

    private final NotificationRepository notificationRepository;
    private final EventIdempotencyPort idempotencyPort;

    public NotificationUseCases(NotificationRepository notificationRepository,
                                 EventIdempotencyPort idempotencyPort) {
        this.notificationRepository = notificationRepository;
        this.idempotencyPort = idempotencyPort;
    }

    /**
     * Creates a notification and records the event as processed.
     * Idempotent: if the eventId has already been processed, this is a no-op.
     * After creation, simulates sending (MVP: log and mark as SENT).
     */
    @Transactional
    public void createAndSendNotification(String userId, NotificationType type,
                                           String title, String message, String eventId) {
        // Idempotency check
        if (idempotencyPort.hasBeenProcessed(eventId)) {
            log.info("Event {} already processed, skipping notification creation", eventId);
            return;
        }

        Notification notification = new Notification(UUID.randomUUID(), userId, type, title, message);

        // MVP: simulate sending by logging and marking as SENT
        log.info("Sending notification to user {}: [{}] {}", userId, type, title);
        notification.markSent();

        notificationRepository.save(notification);
        idempotencyPort.markProcessed(eventId);

        log.info("Notification {} created and marked as SENT for event {}", notification.getId(), eventId);
    }

    /**
     * Marks an existing notification as sent.
     */
    @Transactional
    public void markSent(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationException("Notification not found: " + notificationId));
        notification.markSent();
        notificationRepository.save(notification);
    }

    /**
     * Marks an existing notification as failed.
     */
    @Transactional
    public void markFailed(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationException("Notification not found: " + notificationId));
        notification.markFailed();
        notificationRepository.save(notification);
    }
}
