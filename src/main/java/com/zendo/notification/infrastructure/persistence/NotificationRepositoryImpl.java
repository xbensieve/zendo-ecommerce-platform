package com.zendo.notification.infrastructure.persistence;

import com.zendo.notification.domain.Notification;
import com.zendo.notification.domain.NotificationRepository;
import com.zendo.notification.domain.NotificationStatus;
import com.zendo.notification.domain.NotificationType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class NotificationRepositoryImpl implements NotificationRepository {

    private final SpringDataNotificationRepository springDataRepo;

    public NotificationRepositoryImpl(SpringDataNotificationRepository springDataRepo) {
        this.springDataRepo = springDataRepo;
    }

    @Override
    public void save(Notification notification) {
        NotificationJpaEntity entity = toEntity(notification);
        springDataRepo.save(entity);
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return springDataRepo.findById(id).map(this::toDomain);
    }

    @Override
    public List<Notification> findByUserId(String userId) {
        return springDataRepo.findByUserId(userId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private NotificationJpaEntity toEntity(Notification n) {
        NotificationJpaEntity e = new NotificationJpaEntity(
                n.getId(),
                n.getUserId(),
                n.getType().name(),
                n.getTitle(),
                n.getMessage(),
                n.getStatus().name(),
                n.getCreatedAt(),
                n.getSentAt()
        );
        return e;
    }

    private Notification toDomain(NotificationJpaEntity e) {
        return new Notification(
                e.getId(),
                e.getUserId(),
                NotificationType.valueOf(e.getType()),
                e.getTitle(),
                e.getMessage(),
                NotificationStatus.valueOf(e.getStatus()),
                e.getCreatedAt(),
                e.getSentAt()
        );
    }
}
