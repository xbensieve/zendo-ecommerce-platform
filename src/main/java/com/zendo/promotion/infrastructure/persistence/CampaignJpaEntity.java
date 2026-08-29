package com.zendo.promotion.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "campaigns", schema = "promotion")
public class CampaignJpaEntity {

    @Id
    private UUID id;

    private String name;

    private String status;

    private Instant startDate;

    private Instant endDate;

    private String discountType;

    private BigDecimal discountValue;

    private boolean isGlobal;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "campaign_vendor_scopes", schema = "promotion", joinColumns = @JoinColumn(name = "campaign_id"))
    @Column(name = "vendor_id")
    private Set<UUID> vendorIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "campaign_product_scopes", schema = "promotion", joinColumns = @JoinColumn(name = "campaign_id"))
    @Column(name = "product_id")
    private Set<UUID> productIds = new HashSet<>();

    protected CampaignJpaEntity() {}

    public CampaignJpaEntity(UUID id, String name, String status, Instant startDate, Instant endDate,
                             String discountType, BigDecimal discountValue, boolean isGlobal,
                             Set<UUID> vendorIds, Set<UUID> productIds) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.isGlobal = isGlobal;
        this.vendorIds = vendorIds;
        this.productIds = productIds;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public Instant getStartDate() { return startDate; }
    public Instant getEndDate() { return endDate; }
    public String getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public boolean isGlobal() { return isGlobal; }
    public Set<UUID> getVendorIds() { return vendorIds; }
    public Set<UUID> getProductIds() { return productIds; }
}
