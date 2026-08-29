package com.zendo.notification.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for Notification aggregate.
 */
public interface NotificationRepository {

    void save(Notification notification);

    Optional<Notification> findById(UUID id);

    List<Notification> findByUserId(String userId);
}
