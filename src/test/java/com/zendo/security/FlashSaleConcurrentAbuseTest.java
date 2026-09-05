package com.zendo.security;

import com.zendo.TestcontainersConfiguration;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public class FlashSaleConcurrentAbuseTest {

    @MockBean
    private com.zendo.catalog.api.CatalogQueryApi catalogQueryApi;

    @Autowired
    private FlashSaleCheckoutUseCases flashSaleCheckoutUseCases;

    @Autowired
    private FlashSaleUseCases flashSaleUseCases;

    @Autowired
    private InventoryUseCases inventoryUseCases;

    @Test
    @DisplayName("Concurrent Abuse: Same customer sending 10 parallel requests with different keys must only succeed ONCE")
    void sameCustomer_parallelRequests_mustSucceedExactlyOnce() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        String sku = "ABUSE-SKU-" + UUID.randomUUID().toString().substring(0, 8);

        org.mockito.Mockito.when(catalogQueryApi.getVariantInfo(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(
                java.util.Optional.of(new com.zendo.catalog.api.CatalogQueryApi.ProductVariantInfo(productId.toString(), vendorId.toString(), sku, "Test Product", new BigDecimal("10.00"), "USD"))
        );
        org.mockito.Mockito.when(catalogQueryApi.isProductVariantActive(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        inventoryUseCases.createInventoryItem(vendorId.toString(), sku);
        inventoryUseCases.adjustInventory(productId, 100, "INIT-ABUSE");

        Instant now = Instant.now();
        Instant startTime = now.minus(1, ChronoUnit.HOURS);
        Instant endTime = now.plus(1, ChronoUnit.HOURS);

        // Flash sale with allocation = 50
        FlashSale flashSale = flashSaleUseCases.createFlashSale(
                vendorId, productId, sku, new BigDecimal("10.00"), 50, startTime, endTime
        );
        flashSaleUseCases.activateFlashSale(flashSale.getId());

        String targetCustomer = "abuser-customer-" + UUID.randomUUID();
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger rejectedLimits = new AtomicInteger(0);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final String idempotencyKey = "key-" + i + "-" + UUID.randomUUID();
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                try {
                    flashSaleCheckoutUseCases.checkoutFlashSale(targetCustomer, idempotencyKey, flashSale.getId(), 1);
                    successes.incrementAndGet();
                } catch (OrderException e) {
                    if (e.getMessage().contains("already participated") || e.getMessage().contains("Duplicate checkout")) {
                        rejectedLimits.incrementAndGet();
                    }
                }
                return null;
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Fire all 10 threads at once

        for (Future<Void> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(1, successes.get(), "Customer limit must strictly allow exactly 1 successful purchase");
        assertEquals(threads - 1, rejectedLimits.get(), "All other concurrent attempts must be rejected");
    }
}
