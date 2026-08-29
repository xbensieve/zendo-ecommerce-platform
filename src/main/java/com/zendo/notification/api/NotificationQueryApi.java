package com.zendo.notification.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public query API for the notification module.
 * Other modules may use this to query notifications for a user.
 */
public interface NotificationQueryApi {

    record NotificationSummary(
            UUID id,
            String type,
            String title,
            String message,
            String status,
            Instant createdAt
    ) {}

    List<NotificationSummary> getNotificationsForUser(String userId);
}
