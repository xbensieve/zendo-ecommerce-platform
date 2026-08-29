package com.zendo.catalog.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "product_variants", schema = "catalog")
public class ProductVariantEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(nullable = false)
    private String sku;

    @Column(name = "price_amount", nullable = false)
    private BigDecimal priceAmount;

    @Column(name = "price_currency", nullable = false)
    private String priceCurrency;

    protected ProductVariantEntity() {}

    public ProductVariantEntity(UUID id, UUID vendorId, String sku, BigDecimal priceAmount, String priceCurrency) {
        this.id = id;
        this.vendorId = vendorId;
        this.sku = sku;
        this.priceAmount = priceAmount;
        this.priceCurrency = priceCurrency;
    }

    public void setProduct(ProductEntity product) {
        this.product = product;
    }

    public UUID getId() { return id; }
    public UUID getVendorId() { return vendorId; }
    public String getSku() { return sku; }
    public BigDecimal getPriceAmount() { return priceAmount; }
    public String getPriceCurrency() { return priceCurrency; }
}
