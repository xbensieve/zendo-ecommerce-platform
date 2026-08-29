package com.zendo.notification.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification aggregate root.
 * Represents a notification to be sent to a user in response to a domain event.
 * Pure domain — no framework dependencies.
 */
public class Notification {

    private final UUID id;
    private final String userId;
    private final NotificationType type;
    private final String title;
    private final String message;
    private NotificationStatus status;
    private final Instant createdAt;
    private Instant sentAt;

    public Notification(UUID id, String userId, NotificationType type, String title, String message) {
        if (userId == null || userId.isBlank()) {
            throw new NotificationException("UserId must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new NotificationException("Message must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new NotificationException("Title must not be blank");
        }
        if (type == null) {
            throw new NotificationException("NotificationType must not be null");
        }
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.status = NotificationStatus.PENDING;
        this.createdAt = Instant.now();
        this.sentAt = null;
    }

    /**
     * Reconstitution constructor for persistence layer.
     */
    public Notification(UUID id, String userId, NotificationType type, String title, String message,
                        NotificationStatus status, Instant createdAt, Instant sentAt) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.status = status;
        this.createdAt = createdAt;
        this.sentAt = sentAt;
    }

    /**
     * Mark the notification as successfully sent.
     */
    public void markSent() {
        if (this.status != NotificationStatus.PENDING) {
            throw new NotificationException(
                    "Cannot mark notification as SENT from status " + this.status);
        }
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
    }

    /**
     * Mark the notification as failed to send.
     */
    public void markFailed() {
        if (this.status != NotificationStatus.PENDING) {
            throw new NotificationException(
                    "Cannot mark notification as FAILED from status " + this.status);
        }
        this.status = NotificationStatus.FAILED;
    }

    public UUID getId() { return id; }
    public String getUserId() { return userId; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public NotificationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
}
