package com.zendo.order.domain;

import java.math.BigDecimal;
import java.util.UUID;

public class OrderItem {
    private final UUID id;
    private final UUID productId;
    private final String sku;
    private final String productName;
    private final BigDecimal originalUnitPrice;
    private final BigDecimal appliedDiscount;
    private final BigDecimal finalUnitPrice; // previously unitPrice
    private final String promotionRef;
    private final int quantity;
    private final BigDecimal lineTotal;

    public OrderItem(UUID id, UUID productId, String sku, String productName, BigDecimal originalUnitPrice, BigDecimal appliedDiscount, BigDecimal finalUnitPrice, String promotionRef, int quantity) {
        if (quantity <= 0) {
            throw new OrderException("Quantity must be positive");
        }
        if (finalUnitPrice == null || finalUnitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new OrderException("Final unit price must be non-negative");
        }
        
        this.id = id;
        this.productId = productId;
        this.sku = sku;
        this.productName = productName;
        this.originalUnitPrice = originalUnitPrice;
        this.appliedDiscount = appliedDiscount != null ? appliedDiscount : BigDecimal.ZERO;
        this.finalUnitPrice = finalUnitPrice;
        this.promotionRef = promotionRef;
        this.quantity = quantity;
        this.lineTotal = this.finalUnitPrice.multiply(BigDecimal.valueOf(quantity));
    }
    
    // For reconstitution from DB where line total might already be calculated
    public OrderItem(UUID id, UUID productId, String sku, String productName, BigDecimal originalUnitPrice, BigDecimal appliedDiscount, BigDecimal finalUnitPrice, String promotionRef, int quantity, BigDecimal lineTotal) {
        this.id = id;
        this.productId = productId;
        this.sku = sku;
        this.productName = productName;
        this.originalUnitPrice = originalUnitPrice;
        this.appliedDiscount = appliedDiscount;
        this.finalUnitPrice = finalUnitPrice;
        this.promotionRef = promotionRef;
        this.quantity = quantity;
        this.lineTotal = lineTotal;
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public String getSku() { return sku; }
    public String getProductName() { return productName; }
    public BigDecimal getOriginalUnitPrice() { return originalUnitPrice; }
    public BigDecimal getAppliedDiscount() { return appliedDiscount; }
    public BigDecimal getFinalUnitPrice() { return finalUnitPrice; }
    public String getPromotionRef() { return promotionRef; }
    public int getQuantity() { return quantity; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
