package com.zendo.promotion.domain;

import java.time.Instant;
import java.util.UUID;

public class Campaign {
    private final UUID id;
    private final String name;
    private CampaignStatus status;
    private final Instant startDate;
    private final Instant endDate;
    private final DiscountRule discountRule;
    private final EligibilityScope scope;

    public Campaign(UUID id, String name, Instant startDate, Instant endDate, DiscountRule discountRule, EligibilityScope scope) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new PromotionException("Invalid validity period");
        }
        if (discountRule == null) {
            throw new PromotionException("Campaign must have a discount rule");
        }
        if (scope == null) {
            throw new PromotionException("Campaign must have an eligibility scope");
        }
        this.id = id;
        this.name = name;
        this.status = CampaignStatus.DRAFT;
        this.startDate = startDate;
        this.endDate = endDate;
        this.discountRule = discountRule;
        this.scope = scope;
    }

    // For reconstitution
    public Campaign(UUID id, String name, CampaignStatus status, Instant startDate, Instant endDate, DiscountRule discountRule, EligibilityScope scope) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
        this.discountRule = discountRule;
        this.scope = scope;
    }

    public void schedule() {
        if (this.status != CampaignStatus.DRAFT) {
            throw new PromotionException("Only DRAFT campaigns can be scheduled");
        }
        this.status = CampaignStatus.SCHEDULED;
    }

    public void activate() {
        if (this.status != CampaignStatus.SCHEDULED && this.status != CampaignStatus.DRAFT) {
            throw new PromotionException("Campaign cannot be activated from status " + this.status);
        }
        this.status = CampaignStatus.ACTIVE;
    }

    public void end() {
        if (this.status != CampaignStatus.ACTIVE) {
            throw new PromotionException("Only ACTIVE campaigns can be ended");
        }
        this.status = CampaignStatus.ENDED;
    }

    public void cancel() {
        if (this.status == CampaignStatus.ENDED) {
            throw new PromotionException("Cannot cancel an ENDED campaign");
        }
        this.status = CampaignStatus.CANCELLED;
    }

    public boolean isEligible(UUID vendorId, UUID productId, Instant atTime) {
        if (status != CampaignStatus.ACTIVE) {
            return false;
        }
        if (atTime.isBefore(startDate) || atTime.isAfter(endDate)) {
            return false;
        }
        return scope.isEligible(vendorId, productId);
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public CampaignStatus getStatus() { return status; }
    public Instant getStartDate() { return startDate; }
    public Instant getEndDate() { return endDate; }
    public DiscountRule getDiscountRule() { return discountRule; }
    public EligibilityScope getScope() { return scope; }
}
