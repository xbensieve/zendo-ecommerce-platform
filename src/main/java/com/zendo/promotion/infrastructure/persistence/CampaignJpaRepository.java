package com.zendo.promotion.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CampaignJpaRepository extends JpaRepository<CampaignJpaEntity, UUID> {
    List<CampaignJpaEntity> findByStatus(String status);
}
