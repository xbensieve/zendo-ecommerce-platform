package com.zendo.promotion.domain;

import java.time.Instant;
import java.util.UUID;

public class Coupon {
    private final UUID id;
    private final String code;
    private final Instant startDate;
    private final Instant endDate;
    private final DiscountRule discountRule;
    private final EligibilityScope scope;
    private final int maxUses;
    private int currentUses;
    private boolean active;
    private final Long version;

    public Coupon(UUID id, String code, Instant startDate, Instant endDate, DiscountRule discountRule, EligibilityScope scope, int maxUses) {
        if (code == null || code.trim().isEmpty()) {
            throw new PromotionException("Coupon code cannot be empty");
        }
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new PromotionException("Invalid validity period");
        }
        if (discountRule == null || scope == null) {
            throw new PromotionException("Coupon must have a discount rule and eligibility scope");
        }
        if (maxUses <= 0) {
            throw new PromotionException("Max uses must be positive");
        }
        this.id = id;
        this.code = code.toUpperCase();
        this.startDate = startDate;
        this.endDate = endDate;
        this.discountRule = discountRule;
        this.scope = scope;
        this.maxUses = maxUses;
        this.currentUses = 0;
        this.active = true;
        this.version = null;
    }

    // For reconstitution
    public Coupon(UUID id, String code, Instant startDate, Instant endDate, DiscountRule discountRule, EligibilityScope scope, int maxUses, int currentUses, boolean active, Long version) {
        this.id = id;
        this.code = code;
        this.startDate = startDate;
        this.endDate = endDate;
        this.discountRule = discountRule;
        this.scope = scope;
        this.maxUses = maxUses;
        this.currentUses = currentUses;
        this.active = active;
        this.version = version;
    }

    public void deactivate() {
        this.active = false;
    }

    public void redeem() {
        if (!active) {
            throw new PromotionException("Coupon is not active");
        }
        if (currentUses >= maxUses) {
            throw new PromotionException("Coupon usage limit reached");
        }
        Instant now = Instant.now();
        if (now.isBefore(startDate) || now.isAfter(endDate)) {
            throw new PromotionException("Coupon is not valid at this time");
        }
        this.currentUses++;
    }

    public boolean isEligible(UUID vendorId, UUID productId, Instant atTime) {
        if (!active) {
            return false;
        }
        if (currentUses >= maxUses) {
            return false;
        }
        if (atTime.isBefore(startDate) || atTime.isAfter(endDate)) {
            return false;
        }
        return scope.isEligible(vendorId, productId);
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public Instant getStartDate() { return startDate; }
    public Instant getEndDate() { return endDate; }
    public DiscountRule getDiscountRule() { return discountRule; }
    public EligibilityScope getScope() { return scope; }
    public int getMaxUses() { return maxUses; }
    public int getCurrentUses() { return currentUses; }
    public boolean isActive() { return active; }
    public Long getVersion() { return version; }
}
