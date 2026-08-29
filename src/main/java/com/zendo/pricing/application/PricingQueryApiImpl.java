package com.zendo.pricing.application;

import com.zendo.catalog.api.CatalogQueryApi;
import com.zendo.pricing.api.PricingQueryApi;
import com.zendo.promotion.api.PromotionQueryApi;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
public class PricingQueryApiImpl implements PricingQueryApi {

    private final CatalogQueryApi catalogQueryApi;
    private final PromotionQueryApi promotionQueryApi;

    public PricingQueryApiImpl(CatalogQueryApi catalogQueryApi, PromotionQueryApi promotionQueryApi) {
        this.catalogQueryApi = catalogQueryApi;
        this.promotionQueryApi = promotionQueryApi;
    }

    @Override
    public PricedItem calculateItemPrice(String vendorId, String productId, String sku, int quantity, String couponCode) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        // 1. Get base price from Catalog
        CatalogQueryApi.ProductVariantInfo variantInfo = catalogQueryApi.getVariantInfo(vendorId, sku)
                .orElseThrow(() -> new IllegalArgumentException("Product variant not found or inactive: " + sku));

        BigDecimal originalUnitPrice = variantInfo.priceAmount();
        
        // 2. Get all applicable promotions
        List<PromotionQueryApi.PromotionDetails> applicablePromotions = promotionQueryApi.getApplicablePromotions(vendorId, productId, couponCode);

        BigDecimal bestAppliedDiscount = BigDecimal.ZERO;
        BigDecimal bestFinalUnitPrice = originalUnitPrice;
        String bestPromotionRef = null;

        for (PromotionQueryApi.PromotionDetails promo : applicablePromotions) {
            BigDecimal currentAppliedDiscount = BigDecimal.ZERO;
            BigDecimal currentFinalUnitPrice = originalUnitPrice;
            
            if ("FIXED_AMOUNT".equals(promo.discountType())) {
                currentAppliedDiscount = promo.discountValue().min(originalUnitPrice);
                currentFinalUnitPrice = originalUnitPrice.subtract(currentAppliedDiscount);
            } else if ("PERCENTAGE".equals(promo.discountType())) {
                currentAppliedDiscount = originalUnitPrice.multiply(promo.discountValue())
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                currentFinalUnitPrice = originalUnitPrice.subtract(currentAppliedDiscount);
            }
            
            if (currentFinalUnitPrice.compareTo(BigDecimal.ZERO) < 0) {
                currentFinalUnitPrice = BigDecimal.ZERO;
                currentAppliedDiscount = originalUnitPrice;
            }
            
            // If this promotion gives a better price (lower final price)
            if (currentFinalUnitPrice.compareTo(bestFinalUnitPrice) < 0) {
                bestFinalUnitPrice = currentFinalUnitPrice;
                bestAppliedDiscount = currentAppliedDiscount;
                bestPromotionRef = promo.promotionRef();
            }
        }

        return new PricedItem(
                vendorId,
                productId,
                sku,
                variantInfo.productName(),
                quantity,
                variantInfo.priceCurrency(),
                originalUnitPrice,
                bestAppliedDiscount,
                bestFinalUnitPrice,
                bestPromotionRef
        );
    }
}
