package com.zendo.promotion.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "coupons", schema = "promotion")
public class CouponJpaEntity {

    @Id
    private UUID id;

    @Column(unique = true)
    private String code;

    private Instant startDate;

    private Instant endDate;

    private String discountType;

    private BigDecimal discountValue;

    private boolean isGlobal;

    private int maxUses;

    private int currentUses;

    private boolean active;

    @Version
    private Long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "coupon_vendor_scopes", schema = "promotion", joinColumns = @JoinColumn(name = "coupon_id"))
    @Column(name = "vendor_id")
    private Set<UUID> vendorIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "coupon_product_scopes", schema = "promotion", joinColumns = @JoinColumn(name = "coupon_id"))
    @Column(name = "product_id")
    private Set<UUID> productIds = new HashSet<>();

    protected CouponJpaEntity() {}

    public CouponJpaEntity(UUID id, String code, Instant startDate, Instant endDate,
                           String discountType, BigDecimal discountValue, boolean isGlobal,
                           int maxUses, int currentUses, boolean active, Long version,
                           Set<UUID> vendorIds, Set<UUID> productIds) {
        this.id = id;
        this.code = code;
        this.startDate = startDate;
        this.endDate = endDate;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.isGlobal = isGlobal;
        this.maxUses = maxUses;
        this.currentUses = currentUses;
        this.active = active;
        this.version = version;
        this.vendorIds = vendorIds;
        this.productIds = productIds;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public Instant getStartDate() { return startDate; }
    public Instant getEndDate() { return endDate; }
    public String getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public boolean isGlobal() { return isGlobal; }
    public int getMaxUses() { return maxUses; }
    public int getCurrentUses() { return currentUses; }
    public boolean isActive() { return active; }
    public Long getVersion() { return version; }
    public Set<UUID> getVendorIds() { return vendorIds; }
    public Set<UUID> getProductIds() { return productIds; }
}
