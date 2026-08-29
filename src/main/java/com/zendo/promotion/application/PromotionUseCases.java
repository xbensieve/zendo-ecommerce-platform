package com.zendo.promotion.application;

import com.zendo.promotion.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class PromotionUseCases {

    private final CampaignRepository campaignRepository;
    private final CouponRepository couponRepository;

    public PromotionUseCases(CampaignRepository campaignRepository, CouponRepository couponRepository) {
        this.campaignRepository = campaignRepository;
        this.couponRepository = couponRepository;
    }

    @Transactional
    public UUID createCampaign(String name, Instant startDate, Instant endDate, DiscountType type, BigDecimal value, boolean isGlobal, Set<UUID> vendorIds, Set<UUID> productIds) {
        Campaign campaign = new Campaign(UUID.randomUUID(), name, startDate, endDate, new DiscountRule(type, value), new EligibilityScope(isGlobal, vendorIds, productIds));
        campaignRepository.save(campaign);
        return campaign.getId();
    }

    @Transactional
    public void activateCampaign(UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new PromotionException("Campaign not found"));
        campaign.activate();
        campaignRepository.save(campaign);
    }

    @Transactional
    public UUID createCoupon(String code, Instant startDate, Instant endDate, DiscountType type, BigDecimal value, boolean isGlobal, Set<UUID> vendorIds, Set<UUID> productIds, int maxUses) {
        Coupon coupon = new Coupon(UUID.randomUUID(), code, startDate, endDate, new DiscountRule(type, value), new EligibilityScope(isGlobal, vendorIds, productIds), maxUses);
        couponRepository.save(coupon);
        return coupon.getId();
    }
}
