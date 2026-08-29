package com.zendo.notification.api;

import com.zendo.notification.domain.Notification;
import com.zendo.notification.domain.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationQueryApiImpl implements NotificationQueryApi {

    private final NotificationRepository notificationRepository;

    public NotificationQueryApiImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationSummary> getNotificationsForUser(String userId) {
        return notificationRepository.findByUserId(userId).stream()
                .map(n -> new NotificationSummary(
                        n.getId(),
                        n.getType().name(),
                        n.getTitle(),
                        n.getMessage(),
                        n.getStatus().name(),
                        n.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }
}
