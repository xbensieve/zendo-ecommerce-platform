package com.zendo;

import com.zendo.catalog.api.CatalogQueryApi;
import com.zendo.inventory.application.InventoryUseCases;
import com.zendo.promotion.application.FlashSaleUseCases;
import com.zendo.promotion.domain.FlashSale;
import com.zendo.security.infrastructure.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public class FlashSaleHttpLoadTest {

    private static final Logger log = LoggerFactory.getLogger(FlashSaleHttpLoadTest.class);

    @LocalServerPort
    private int port;

    @Container
    static org.testcontainers.containers.RabbitMQContainer rabbitMQContainer = new org.testcontainers.containers.RabbitMQContainer(org.testcontainers.utility.DockerImageName.parse("rabbitmq:3.12-management"));

    @org.springframework.test.context.DynamicPropertySource
    static void configureProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQContainer::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQContainer::getAdminPassword);
    }

    @MockBean
    private CatalogQueryApi catalogQueryApi;

    @Autowired
    private FlashSaleUseCases flashSaleUseCases;

    @Autowired
    private InventoryUseCases inventoryUseCases;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void runModerateLoadProfile() throws InterruptedException {
        runLoadTest(500, 100);
    }

    @Test
    void runHighLoadProfile() throws InterruptedException {
        runLoadTest(2000, 100);
    }

    @Test
    void runExtremeBurstProfile() throws InterruptedException {
        runLoadTest(10000, 100);
    }

    private void runLoadTest(int concurrentClients, int flashSaleAllocation) throws InterruptedException {
        log.info("Starting Load Profile: {} concurrent clients, {} allocation", concurrentClients, flashSaleAllocation);
        
        String sku = "HTTP-FLASH-" + UUID.randomUUID().toString().substring(0, 8);
        UUID vendorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        int physicalStock = 100;

        when(catalogQueryApi.getVariantInfo(anyString(), anyString())).thenReturn(
                java.util.Optional.of(new CatalogQueryApi.ProductVariantInfo(productId.toString(), vendorId.toString(), sku, "Test Product", new BigDecimal("10.00"), "USD"))
        );
        when(catalogQueryApi.isProductVariantActive(anyString(), anyString())).thenReturn(true);

        inventoryUseCases.createInventoryItem(vendorId.toString(), sku);
        inventoryUseCases.adjustInventory(productId, physicalStock, "init");

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

        ExecutorService executorService = Executors.newFixedThreadPool(Math.min(concurrentClients, 500)); // Cap threads to 500 to avoid OS thread limit, simulate concurrency with Future scheduling
        List<Callable<Long>> tasks = new ArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger reject4xxCount = new AtomicInteger(0);
        AtomicInteger reject5xxCount = new AtomicInteger(0);

        String url = "http://localhost:" + port + "/orders/flash-sales/" + flashSale.getId() + "/purchase";
        
        for (int i = 0; i < concurrentClients; i++) {
            final String customerId = "cust-load-" + i;
            final String idempotencyKey = UUID.randomUUID().toString();
            final String token = jwtService.generateToken(customerId, List.of("CUSTOMER"));

            tasks.add(() -> {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(token);
                headers.set("Idempotency-Key", idempotencyKey);

                Map<String, Object> body = Map.of("quantity", 1);
                HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

                startLatch.await(); // Unleash the herd

                long start = System.currentTimeMillis();
                try {
                    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
                    long duration = System.currentTimeMillis() - start;
                    
                    if (response.getStatusCode().is2xxSuccessful()) {
                        successCount.incrementAndGet();
                    } else if (response.getStatusCode().is4xxClientError()) {
                        if (reject4xxCount.incrementAndGet() == 1) {
                            log.error("First 4xx Error: Status={} Body={}", response.getStatusCode(), response.getBody());
                        }
                    } else if (response.getStatusCode().is5xxServerError()) {
                        reject5xxCount.incrementAndGet();
                        log.warn("5xx Error: {}", response.getBody());
                    }
                    return duration;
                } catch (Exception e) {
                    log.error("Request failed", e);
                    reject5xxCount.incrementAndGet();
                    return System.currentTimeMillis() - start;
                }
            });
        }

        long testStartTime = System.currentTimeMillis();
        List<Future<Long>> futures = new ArrayList<>();
        for (Callable<Long> task : tasks) {
            futures.add(executorService.submit(task));
        }

        startLatch.countDown(); 

        List<Long> latencies = new ArrayList<>();
        for (Future<Long> future : futures) {
            try {
                latencies.add(future.get());
            } catch (Exception e) {
                // Ignore
            }
        }

        long testDuration = System.currentTimeMillis() - testStartTime;
        executorService.shutdown();

        latencies.sort(Long::compareTo);
        long p50 = latencies.get((int) (latencies.size() * 0.5));
        long p95 = latencies.get((int) (latencies.size() * 0.95));
        long p99 = latencies.get((int) (latencies.size() * 0.99));

        log.info("--- LOAD PROFILE RESULTS ---");
        log.info("Clients: {}, Duration: {} ms", concurrentClients, testDuration);
        log.info("Throughput: {} req/s", (concurrentClients * 1000L) / Math.max(1, testDuration));
        log.info("Success: {}, 4xx: {}, 5xx: {}", successCount.get(), reject4xxCount.get(), reject5xxCount.get());
        log.info("Latencies - p50: {} ms, p95: {} ms, p99: {} ms", p50, p95, p99);

        // Assertions
        assertEquals(flashSaleAllocation, successCount.get(), "Successful purchases should match allocation exactly");

        // Verify Flash Sale Allocation in DB
        var flashSaleRow = jdbcClient.sql("SELECT available_quantity FROM promotion.flash_sales WHERE id = ?")
                .param(flashSale.getId())
                .query().singleRow();
        assertEquals(0, flashSaleRow.get("available_quantity"));

        // Verify Physical Inventory in DB
        var inventoryRow = jdbcClient.sql("SELECT on_hand_quantity, reserved_quantity, available_quantity FROM inventory.inventory_items WHERE product_id = ?")
                .param(productId)
                .query().singleRow();
        assertEquals(physicalStock, inventoryRow.get("on_hand_quantity"));
        assertEquals(flashSaleAllocation, inventoryRow.get("reserved_quantity"));
        assertEquals(physicalStock - flashSaleAllocation, inventoryRow.get("available_quantity"));
        
        log.info("--- Correctness verified: No overselling, perfect state. ---");
    }
}
