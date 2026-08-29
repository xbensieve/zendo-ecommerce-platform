package com.zendo.promotion.infrastructure.persistence;

import com.zendo.promotion.domain.*;

public class PromotionMapper {

    public static Campaign toDomain(CampaignJpaEntity entity) {
        return new Campaign(
                entity.getId(),
                entity.getName(),
                CampaignStatus.valueOf(entity.getStatus()),
                entity.getStartDate(),
                entity.getEndDate(),
                new DiscountRule(DiscountType.valueOf(entity.getDiscountType()), entity.getDiscountValue()),
                new EligibilityScope(entity.isGlobal(), entity.getVendorIds(), entity.getProductIds())
        );
    }

    public static CampaignJpaEntity toEntity(Campaign campaign) {
        return new CampaignJpaEntity(
                campaign.getId(),
                campaign.getName(),
                campaign.getStatus().name(),
                campaign.getStartDate(),
                campaign.getEndDate(),
                campaign.getDiscountRule().getType().name(),
                campaign.getDiscountRule().getValue(),
                campaign.getScope().isGlobal(),
                campaign.getScope().getVendorIds(),
                campaign.getScope().getProductIds()
        );
    }

    public static Coupon toDomain(CouponJpaEntity entity) {
        return new Coupon(
                entity.getId(),
                entity.getCode(),
                entity.getStartDate(),
                entity.getEndDate(),
                new DiscountRule(DiscountType.valueOf(entity.getDiscountType()), entity.getDiscountValue()),
                new EligibilityScope(entity.isGlobal(), entity.getVendorIds(), entity.getProductIds()),
                entity.getMaxUses(),
                entity.getCurrentUses(),
                entity.isActive(),
                entity.getVersion()
        );
    }

    public static CouponJpaEntity toEntity(Coupon coupon) {
        return new CouponJpaEntity(
                coupon.getId(),
                coupon.getCode(),
                coupon.getStartDate(),
                coupon.getEndDate(),
                coupon.getDiscountRule().getType().name(),
                coupon.getDiscountRule().getValue(),
                coupon.getScope().isGlobal(),
                coupon.getMaxUses(),
                coupon.getCurrentUses(),
                coupon.isActive(),
                coupon.getVersion(),
                coupon.getScope().getVendorIds(),
                coupon.getScope().getProductIds()
        );
    }
}
