package com.zendo.pricing.application;

import com.zendo.catalog.api.CatalogQueryApi;
import com.zendo.pricing.api.PricingQueryApi;
import com.zendo.promotion.api.PromotionQueryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PricingQueryApiImplTest {

    private CatalogQueryApi catalogQueryApi;
    private PromotionQueryApi promotionQueryApi;
    private PricingQueryApi pricingQueryApi;

    @BeforeEach
    void setUp() {
        catalogQueryApi = mock(CatalogQueryApi.class);
        promotionQueryApi = mock(PromotionQueryApi.class);
        pricingQueryApi = new PricingQueryApiImpl(catalogQueryApi, promotionQueryApi);
    }

    @Test
    void shouldReturnBasePriceWhenNoPromotions() {
        when(catalogQueryApi.getVariantInfo(anyString(), anyString())).thenReturn(
                Optional.of(new CatalogQueryApi.ProductVariantInfo("product", "vendor", "SKU123", "Product", new BigDecimal("100.00"), "USD"))
        );
        when(promotionQueryApi.getApplicablePromotions(anyString(), anyString(), anyString())).thenReturn(List.of());

        PricingQueryApi.PricedItem item = pricingQueryApi.calculateItemPrice("vendor", "product", "SKU123", 1, null);

        assertEquals(0, new BigDecimal("100.00").compareTo(item.originalUnitPrice()));
        assertEquals(0, BigDecimal.ZERO.compareTo(item.appliedDiscount()));
        assertEquals(0, new BigDecimal("100.00").compareTo(item.finalUnitPrice()));
    }

    @Test
    void shouldApplyPercentageDiscount() {
        when(catalogQueryApi.getVariantInfo(anyString(), anyString())).thenReturn(
                Optional.of(new CatalogQueryApi.ProductVariantInfo("product", "vendor", "SKU123", "Product", new BigDecimal("100.00"), "USD"))
        );
        when(promotionQueryApi.getApplicablePromotions(anyString(), anyString(), anyString())).thenReturn(
                List.of(new PromotionQueryApi.PromotionDetails("PROMO1", "PERCENTAGE", new BigDecimal("20")))
        );

        PricingQueryApi.PricedItem item = pricingQueryApi.calculateItemPrice("vendor", "product", "SKU123", 1, "CODE");

        assertEquals(0, new BigDecimal("100.00").compareTo(item.originalUnitPrice()));
        assertEquals(0, new BigDecimal("20.00").compareTo(item.appliedDiscount()));
        assertEquals(0, new BigDecimal("80.00").compareTo(item.finalUnitPrice()));
        assertEquals("PROMO1", item.promotionRef());
    }

    @Test
    void shouldApplyFixedAmountDiscount() {
        when(catalogQueryApi.getVariantInfo(anyString(), anyString())).thenReturn(
                Optional.of(new CatalogQueryApi.ProductVariantInfo("product", "vendor", "SKU123", "Product", new BigDecimal("100.00"), "USD"))
        );
        when(promotionQueryApi.getApplicablePromotions(anyString(), anyString(), anyString())).thenReturn(
                List.of(new PromotionQueryApi.PromotionDetails("PROMO1", "FIXED_AMOUNT", new BigDecimal("35.00")))
        );

        PricingQueryApi.PricedItem item = pricingQueryApi.calculateItemPrice("vendor", "product", "SKU123", 1, "CODE");

        assertEquals(0, new BigDecimal("100.00").compareTo(item.originalUnitPrice()));
        assertEquals(0, new BigDecimal("35.00").compareTo(item.appliedDiscount()));
        assertEquals(0, new BigDecimal("65.00").compareTo(item.finalUnitPrice()));
    }

    @Test
    void shouldPickBestPromotionWhenMultipleAvailable() {
        when(catalogQueryApi.getVariantInfo(anyString(), anyString())).thenReturn(
                Optional.of(new CatalogQueryApi.ProductVariantInfo("product", "vendor", "SKU123", "Product", new BigDecimal("100.00"), "USD"))
        );
        when(promotionQueryApi.getApplicablePromotions(anyString(), anyString(), anyString())).thenReturn(
                List.of(
                        new PromotionQueryApi.PromotionDetails("PROMO1", "FIXED_AMOUNT", new BigDecimal("10.00")),
                        new PromotionQueryApi.PromotionDetails("PROMO2", "PERCENTAGE", new BigDecimal("50")), // Better
                        new PromotionQueryApi.PromotionDetails("PROMO3", "FIXED_AMOUNT", new BigDecimal("20.00"))
                )
        );

        PricingQueryApi.PricedItem item = pricingQueryApi.calculateItemPrice("vendor", "product", "SKU123", 1, "CODE");

        assertEquals(0, new BigDecimal("50.00").compareTo(item.appliedDiscount()));
        assertEquals(0, new BigDecimal("50.00").compareTo(item.finalUnitPrice()));
        assertEquals("PROMO2", item.promotionRef());
    }

    @Test
    void shouldClampToMinimumUnitPriceOn100PercentDiscount() {
        when(catalogQueryApi.getVariantInfo(anyString(), anyString())).thenReturn(
                Optional.of(new CatalogQueryApi.ProductVariantInfo("product", "vendor", "SKU123", "Product", new BigDecimal("100.00"), "USD"))
        );
        when(promotionQueryApi.getApplicablePromotions(anyString(), anyString(), anyString())).thenReturn(
                List.of(new PromotionQueryApi.PromotionDetails("PROMO_FREE", "PERCENTAGE", new BigDecimal("100")))
        );

        PricingQueryApi.PricedItem item = pricingQueryApi.calculateItemPrice("vendor", "product", "SKU123", 1, "FREE100");

        assertEquals(0, new BigDecimal("0.01").compareTo(item.finalUnitPrice()));
        assertEquals(0, new BigDecimal("99.99").compareTo(item.appliedDiscount()));
    }

    @Test
    void shouldClampToMinimumUnitPriceOnExcessiveFixedAmountDiscount() {
        when(catalogQueryApi.getVariantInfo(anyString(), anyString())).thenReturn(
                Optional.of(new CatalogQueryApi.ProductVariantInfo("product", "vendor", "SKU123", "Product", new BigDecimal("50.00"), "USD"))
        );
        when(promotionQueryApi.getApplicablePromotions(anyString(), anyString(), anyString())).thenReturn(
                List.of(new PromotionQueryApi.PromotionDetails("PROMO_EXCESS", "FIXED_AMOUNT", new BigDecimal("200.00")))
        );

        PricingQueryApi.PricedItem item = pricingQueryApi.calculateItemPrice("vendor", "product", "SKU123", 1, "BIGDISCOUNT");

        assertEquals(0, new BigDecimal("0.01").compareTo(item.finalUnitPrice()));
        assertEquals(0, new BigDecimal("49.99").compareTo(item.appliedDiscount()));
    }
}
