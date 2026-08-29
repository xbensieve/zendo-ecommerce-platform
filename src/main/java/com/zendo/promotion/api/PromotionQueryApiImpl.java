package com.zendo.promotion.api;

import com.zendo.promotion.domain.Campaign;
import com.zendo.promotion.domain.CampaignRepository;
import com.zendo.promotion.domain.Coupon;
import com.zendo.promotion.domain.CouponRepository;
import com.zendo.promotion.domain.PromotionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PromotionQueryApiImpl implements PromotionQueryApi {

    private final CampaignRepository campaignRepository;
    private final CouponRepository couponRepository;

    public PromotionQueryApiImpl(CampaignRepository campaignRepository, CouponRepository couponRepository) {
        this.campaignRepository = campaignRepository;
        this.couponRepository = couponRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionDetails> getApplicablePromotions(String vendorId, String productId, String couponCode) {
        UUID vId = UUID.fromString(vendorId);
        UUID pId = UUID.fromString(productId);
        Instant now = Instant.now();

        List<PromotionDetails> applicable = new java.util.ArrayList<>();

        // Check active campaigns
        List<Campaign> campaigns = campaignRepository.findActiveCampaigns();
        for (Campaign c : campaigns) {
            if (c.isEligible(vId, pId, now)) {
                applicable.add(new PromotionDetails("CAMPAIGN:" + c.getId(), c.getDiscountRule().getType().name(), c.getDiscountRule().getValue()));
            }
        }

        if (couponCode != null && !couponCode.isBlank()) {
            couponRepository.findByCode(couponCode)
                    .filter(c -> c.isEligible(vId, pId, now))
                    .ifPresent(c -> applicable.add(new PromotionDetails("COUPON:" + c.getCode(), c.getDiscountRule().getType().name(), c.getDiscountRule().getValue())));
        }

        return applicable;
    }
}
