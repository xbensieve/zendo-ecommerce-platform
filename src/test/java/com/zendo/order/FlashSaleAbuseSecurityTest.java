package com.zendo.order;

import com.zendo.catalog.api.CatalogQueryApi;
import com.zendo.inventory.application.InventoryUseCases;
import com.zendo.order.application.FlashSaleCheckoutUseCases;
import com.zendo.order.domain.OrderException;
import com.zendo.promotion.application.FlashSaleUseCases;
import com.zendo.promotion.domain.FlashSale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
public class FlashSaleAbuseSecurityTest {

    @MockBean
    private CatalogQueryApi catalogQueryApi;

    @Autowired
    private FlashSaleCheckoutUseCases flashSaleCheckoutUseCases;

    @Autowired
    private FlashSaleUseCases flashSaleUseCases;

    @Autowired
    private InventoryUseCases inventoryUseCases;

    @Test
    @DisplayName("A customer cannot purchase more than once from the same flash sale, even with different idempotency keys")
    void customerCannotPurchaseTwice_fromSameFlashSale() {
        UUID vendorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String sku = "SKU-ABUSE-" + UUID.randomUUID().toString().substring(0, 8);

        org.mockito.Mockito.when(catalogQueryApi.getVariantInfo(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(
                java.util.Optional.of(new CatalogQueryApi.ProductVariantInfo(productId.toString(), vendorId.toString(), sku, "Test Product", new BigDecimal("10.00"), "USD"))
        );
        org.mockito.Mockito.when(catalogQueryApi.isProductVariantActive(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        inventoryUseCases.createInventoryItem(vendorId.toString(), sku);
        inventoryUseCases.adjustInventory(productId, 100, "init");

        FlashSale flashSale = flashSaleUseCases.createFlashSale(
                vendorId,
                productId,
                sku,
                BigDecimal.valueOf(49.99),
                10,
                Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().plus(1, ChronoUnit.HOURS)
        );
        flashSaleUseCases.activateFlashSale(flashSale.getId());

        String customerId = "cust-" + UUID.randomUUID();

        // 1. First purchase attempt -> succeeds
        var order1 = flashSaleCheckoutUseCases.checkoutFlashSale(
                customerId,
                "key-attempt-1",
                flashSale.getId(),
                1
        );
        assertThat(order1).isNotNull();

        // 2. Second purchase attempt by SAME customer with DIFFERENT idempotency key -> MUST FAIL
        assertThatThrownBy(() -> flashSaleCheckoutUseCases.checkoutFlashSale(
                customerId,
                "key-attempt-2",
                flashSale.getId(),
                1
        ))
        .isInstanceOf(OrderException.class)
        .hasMessageContaining("Customer has already participated in this flash sale");
    }

    @Test
    @DisplayName("Idempotency keys are scoped per-customer, preventing cross-user key collision/hijacking")
    void idempotencyKeysAreScopedPerCustomer() {
        UUID vendorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String sku = "SKU-ISOLATION-" + UUID.randomUUID().toString().substring(0, 8);

        org.mockito.Mockito.when(catalogQueryApi.getVariantInfo(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(
                java.util.Optional.of(new CatalogQueryApi.ProductVariantInfo(productId.toString(), vendorId.toString(), sku, "Test Product", new BigDecimal("10.00"), "USD"))
        );
        org.mockito.Mockito.when(catalogQueryApi.isProductVariantActive(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        inventoryUseCases.createInventoryItem(vendorId.toString(), sku);
        inventoryUseCases.adjustInventory(productId, 100, "init");

        FlashSale flashSale = flashSaleUseCases.createFlashSale(
                vendorId,
                productId,
                sku,
                BigDecimal.valueOf(29.99),
                20,
                Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().plus(1, ChronoUnit.HOURS)
        );
        flashSaleUseCases.activateFlashSale(flashSale.getId());

        String customerA = "cust-A-" + UUID.randomUUID();
        String customerB = "cust-B-" + UUID.randomUUID();
        String sharedKey = "shared-idempotency-key";

        // Customer A purchases using sharedKey
        var orderA = flashSaleCheckoutUseCases.checkoutFlashSale(
                customerA,
                sharedKey,
                flashSale.getId(),
                1
        );

        // Customer B purchases using the SAME sharedKey
        var orderB = flashSaleCheckoutUseCases.checkoutFlashSale(
                customerB,
                sharedKey,
                flashSale.getId(),
                1
        );

        // Both orders succeed and are distinct
        assertThat(orderA.getId()).isNotEqualTo(orderB.getId());
        assertThat(orderA.getCustomerId()).isEqualTo(customerA);
        assertThat(orderB.getCustomerId()).isEqualTo(customerB);
    }
}
