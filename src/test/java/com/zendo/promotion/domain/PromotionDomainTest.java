package com.zendo.promotion.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PromotionDomainTest {

    @Test
    void shouldCalculatePercentageDiscountCorrectly() {
        DiscountRule rule = new DiscountRule(DiscountType.PERCENTAGE, new BigDecimal("10"));
        BigDecimal discounted = rule.applyDiscount(new BigDecimal("100.00"));
        assertEquals(new BigDecimal("90.00"), discounted);
    }

    @Test
    void shouldCalculateFixedDiscountCorrectly() {
        DiscountRule rule = new DiscountRule(DiscountType.FIXED_AMOUNT, new BigDecimal("15.50"));
        BigDecimal discounted = rule.applyDiscount(new BigDecimal("50.00"));
        assertEquals(new BigDecimal("34.50"), discounted);
    }

    @Test
    void fixedDiscountShouldNotGoBelowZero() {
        DiscountRule rule = new DiscountRule(DiscountType.FIXED_AMOUNT, new BigDecimal("50.00"));
        BigDecimal discounted = rule.applyDiscount(new BigDecimal("30.00"));
        assertEquals(0, BigDecimal.ZERO.compareTo(discounted));
    }

    @Test
    void shouldTransitionCampaignStates() {
        Instant start = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant end = Instant.now().plus(1, ChronoUnit.DAYS);
        
        Campaign campaign = new Campaign(
                UUID.randomUUID(), 
                "Summer Sale", 
                start, 
                end, 
                new DiscountRule(DiscountType.PERCENTAGE, new BigDecimal("20")),
                EligibilityScope.global()
        );

        assertEquals(CampaignStatus.DRAFT, campaign.getStatus());
        
        campaign.schedule();
        assertEquals(CampaignStatus.SCHEDULED, campaign.getStatus());
        
        campaign.activate();
        assertEquals(CampaignStatus.ACTIVE, campaign.getStatus());
        
        campaign.end();
        assertEquals(CampaignStatus.ENDED, campaign.getStatus());
    }

    @Test
    void couponRedemptionShouldEnforceLimit() {
        Instant start = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant end = Instant.now().plus(1, ChronoUnit.DAYS);
        
        Coupon coupon = new Coupon(
                UUID.randomUUID(), 
                "SAVE10", 
                start, 
                end, 
                new DiscountRule(DiscountType.FIXED_AMOUNT, new BigDecimal("10")),
                EligibilityScope.global(),
                2 // max uses
        );
        
        assertEquals(0, coupon.getCurrentUses());
        
        coupon.redeem();
        assertEquals(1, coupon.getCurrentUses());
        
        coupon.redeem();
        assertEquals(2, coupon.getCurrentUses());
        
        assertThrows(PromotionException.class, coupon::redeem);
    }

    @Test
    void shouldThrowExceptionWhenMaxUsesExceeded() {
        Coupon coupon = new Coupon(
                UUID.randomUUID(), 
                "LIMIT1", 
                Instant.now().minus(1, ChronoUnit.DAYS), 
                Instant.now().plus(1, ChronoUnit.DAYS), 
                new DiscountRule(DiscountType.PERCENTAGE, new BigDecimal("10")), 
                EligibilityScope.global(),
                1 // Max 1 use
        );

        // First use should succeed
        coupon.redeem();
        
        // Second use should throw exception
        assertThrows(PromotionException.class, coupon::redeem);
    }
}
