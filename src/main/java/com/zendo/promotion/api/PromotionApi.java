package com.zendo.promotion.api;

public interface PromotionApi {
    /**
     * Redeem a coupon code. Should be called during checkout finalization.
     */
    void redeemCoupon(String couponCode);
    
    /**
     * Atomically reserve flash sale allocation.
     * @return true if successful, false if insufficient allocation.
     */
    boolean reserveFlashSaleAllocation(java.util.UUID flashSaleId, int quantity);
    
    java.util.Optional<FlashSaleDetails> getFlashSale(java.util.UUID flashSaleId);
    
    record FlashSaleDetails(
            java.util.UUID id,
            java.util.UUID vendorId,
            java.util.UUID productId,
            String sku,
            java.math.BigDecimal flashPrice
    ) {}
}
