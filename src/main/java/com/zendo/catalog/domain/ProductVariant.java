package com.zendo.catalog.domain;

import java.math.BigDecimal;

public class ProductVariant {
    private final ProductVariantId id;
    private final SKU sku;
    private Money price;

    public ProductVariant(ProductVariantId id, SKU sku, Money price) {
        this.id = id;
        this.sku = sku;
        this.price = price;
    }

    public ProductVariantId getId() {
        return id;
    }

    public SKU getSku() {
        return sku;
    }

    public Money getPrice() {
        return price;
    }

    public void updatePrice(Money newPrice) {
        this.price = newPrice;
    }

    public boolean isValidForPublishing() {
        return price.amount().compareTo(BigDecimal.ZERO) > 0;
    }
}
