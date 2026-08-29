package com.zendo.promotion.infrastructure.persistence;

import com.zendo.promotion.domain.Campaign;
import com.zendo.promotion.domain.CampaignRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CampaignRepositoryImpl implements CampaignRepository {

    private final CampaignJpaRepository jpaRepository;

    public CampaignRepositoryImpl(CampaignJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Campaign campaign) {
        jpaRepository.save(PromotionMapper.toEntity(campaign));
    }

    @Override
    public Optional<Campaign> findById(UUID id) {
        return jpaRepository.findById(id).map(PromotionMapper::toDomain);
    }

    @Override
    public List<Campaign> findActiveCampaigns() {
        return jpaRepository.findByStatus("ACTIVE").stream()
                .map(PromotionMapper::toDomain)
                .toList();
    }
}
