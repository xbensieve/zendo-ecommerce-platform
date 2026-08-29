package com.zendo.pricing.api;

import java.math.BigDecimal;

public interface PricingQueryApi {

    record PricedItem(
            String vendorId,
            String productId,
            String sku,
            String productName,
            int quantity,
            String currency,
            BigDecimal originalUnitPrice,
            BigDecimal appliedDiscount,
            BigDecimal finalUnitPrice,
            String promotionRef
    ) {}

    /**
     * Calculates the final price for a specific product item, considering base price and promotions.
     */
    PricedItem calculateItemPrice(String vendorId, String productId, String sku, int quantity, String couponCode);
}
