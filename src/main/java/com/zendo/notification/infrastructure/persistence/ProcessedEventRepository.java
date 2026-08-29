package com.zendo.notification.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEventJpaEntity, String> {
}
