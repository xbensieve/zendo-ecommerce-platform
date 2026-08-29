package com.zendo;

import com.zendo.inventory.application.InventoryUseCases;
import com.zendo.inventory.domain.InventoryItem;
import com.zendo.inventory.domain.InventoryRepository;
import com.zendo.inventory.domain.Quantity;
import com.zendo.shared.messaging.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.springframework.context.annotation.Import;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class InventoryConcurrencyIntegrationTest {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryUseCases inventoryUseCases;

    @MockBean
    private EventPublisher eventPublisher;

    private UUID productId;
    private UUID vendorId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        vendorId = UUID.randomUUID();
        
        // Setup initial stock of 10
        InventoryItem item = InventoryItem.initialize(productId, vendorId);
        item.adjustStock(new Quantity(10), "INITIAL_SETUP");
        inventoryRepository.save(item);
    }

    @Test
    void reserveInventory_HighConcurrency_PreventsOverselling() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        AtomicInteger successfulReservations = new AtomicInteger(0);
        AtomicInteger failedReservations = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    boolean success = inventoryUseCases.reserveInventory(productId, 1, "ORDER-REF-" + index);
                    if (success) {
                        successfulReservations.incrementAndGet();
                    } else {
                        failedReservations.incrementAndGet();
                    }
                } catch (Exception e) {
                    failedReservations.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // 10 units available, 50 attempts
        assertEquals(10, successfulReservations.get(), "Exactly 10 reservations should succeed");
        assertEquals(40, failedReservations.get(), "Exactly 40 reservations should fail");

        // Verify database state
        InventoryItem dbItem = inventoryRepository.findByProductId(productId).orElseThrow();
        assertEquals(10, dbItem.getOnHand().value());
        assertEquals(10, dbItem.getReserved().value());
        assertEquals(0, dbItem.getAvailable().value());
    }
}
