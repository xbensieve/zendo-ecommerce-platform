package com.zendo;

import com.zendo.inventory.application.InventoryUseCases;
import com.zendo.order.application.FlashSaleCheckoutUseCases;
import com.zendo.order.domain.OrderException;
import com.zendo.promotion.application.FlashSaleUseCases;
import com.zendo.promotion.domain.FlashSale;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FlashSaleConcurrencyIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(FlashSaleConcurrencyIntegrationTest.class);

    @org.testcontainers.junit.jupiter.Container
    static org.testcontainers.containers.RabbitMQContainer rabbitMQContainer = new org.testcontainers.containers.RabbitMQContainer(org.testcontainers.utility.DockerImageName.parse("rabbitmq:3.12-management"));

    @org.springframework.test.context.DynamicPropertySource
    static void configureProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQContainer::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQContainer::getAdminPassword);
    }

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.zendo.catalog.api.CatalogQueryApi catalogQueryApi;

    @Autowired
    private FlashSaleCheckoutUseCases flashSaleCheckoutUseCases;
    
    @Autowired
    private FlashSaleUseCases flashSaleUseCases;
    
    @Autowired
    private InventoryUseCases inventoryUseCases;
    
    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void concurrentFlashSalePurchase_DoesNotOversell() throws InterruptedException {
        String sku = "FLASH-SKU-001";
        UUID vendorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        int flashSaleAllocation = 100;
        int physicalStock = 100;

        org.mockito.Mockito.when(catalogQueryApi.getVariantInfo(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(
                java.util.Optional.of(new com.zendo.catalog.api.CatalogQueryApi.ProductVariantInfo(productId.toString(), vendorId.toString(), sku, "Test Product", new BigDecimal("10.00"), "USD"))
        );
        org.mockito.Mockito.when(catalogQueryApi.isProductVariantActive(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        inventoryUseCases.createInventoryItem(vendorId.toString(), sku);
        inventoryUseCases.adjustInventory(productId, physicalStock, "init");

        // 2. Setup Flash Sale
        Instant now = Instant.now();
        FlashSale flashSale = flashSaleUseCases.createFlashSale(
                vendorId,
                productId,
                sku,
                new BigDecimal("99.99"),
                flashSaleAllocation,
                now.minus(1, ChronoUnit.MINUTES),
                now.plus(1, ChronoUnit.HOURS)
        );
        flashSaleUseCases.activateFlashSale(flashSale.getId());

        int threadCount = 1000; // Simulate 1,000 concurrent buyers
        List<Callable<Boolean>> tasks = new ArrayList<>();
        CountDownLatch readyLatch = new CountDownLatch(threadCount); // Wait for all to be ready
        CountDownLatch startLatch = new CountDownLatch(1); // The starting gun
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String customerId = "cust-flash-" + i;
            final String idempotencyKey = UUID.randomUUID().toString();
            
            tasks.add(() -> {
                readyLatch.countDown(); // Announce this thread is ready
                startLatch.await(); // Wait for the starting gun
                try {
                    flashSaleCheckoutUseCases.checkoutFlashSale(customerId, idempotencyKey, flashSale.getId(), 1);
                    successCount.incrementAndGet();
                    return true;
                } catch (OrderException e) {
                    rejectCount.incrementAndGet();
                    return false;
                } catch (Exception e) {
                    log.error("Unexpected error during purchase", e);
                    rejectCount.incrementAndGet();
                    return false;
                }
            });
        }

        long startTime = System.currentTimeMillis();
        
        try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<Boolean> task : tasks) {
                futures.add(executorService.submit(task));
            }
            
            readyLatch.await(); // Wait until all 1000 virtual threads have started and are waiting
            startLatch.countDown(); // Unleash the herd

            for (Future<Boolean> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Task execution interrupted", e);
                } catch (Exception e) {
                    throw new RuntimeException("Task execution failed", e);
                }
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("Processed {} concurrent flash sale requests in {} ms", threadCount, duration);
        log.info("Successful purchases: {}", successCount.get());
        log.info("Rejected purchases: {}", rejectCount.get());

        // Assertions
        assertEquals(flashSaleAllocation, successCount.get(), "Successful purchases should exactly match allocation");

        // Verify Flash Sale Allocation
        var flashSaleRow = jdbcClient.sql("SELECT * FROM promotion.flash_sales WHERE id = ?")
                .param(flashSale.getId())
                .query().singleRow();
        assertEquals(0, flashSaleRow.get("available_quantity"), "Flash sale allocation should be exactly 0");

        // Verify Physical Inventory
        var inventoryRow = jdbcClient.sql("SELECT * FROM inventory.inventory_items WHERE product_id = ?")
                .param(productId)
                .query().singleRow();
        assertEquals(physicalStock, inventoryRow.get("on_hand_quantity"));
        assertEquals(flashSaleAllocation, inventoryRow.get("reserved_quantity"));
        assertEquals(physicalStock - flashSaleAllocation, inventoryRow.get("available_quantity"), "Available physical stock should be exactly 0");
    }
}
