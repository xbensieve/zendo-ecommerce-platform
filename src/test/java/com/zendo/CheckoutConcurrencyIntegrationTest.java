package com.zendo;

import com.zendo.cart.application.CartUseCases;
import com.zendo.catalog.api.CatalogQueryApi;
import com.zendo.inventory.application.InventoryUseCases;
import com.zendo.order.application.CheckoutUseCases;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CheckoutConcurrencyIntegrationTest {

    @org.testcontainers.junit.jupiter.Container
    static org.testcontainers.containers.RabbitMQContainer rabbitMQContainer = new org.testcontainers.containers.RabbitMQContainer(org.testcontainers.utility.DockerImageName.parse("rabbitmq:3.12-management"));

    @org.springframework.test.context.DynamicPropertySource
    static void configureProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQContainer::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQContainer::getAdminPassword);
    }

    @Autowired
    private CheckoutUseCases checkoutUseCases;
    
    @Autowired
    private CartUseCases cartUseCases;
    
    @Autowired
    private InventoryUseCases inventoryUseCases;
    
    @Autowired
    private JdbcClient jdbcClient;

    @MockBean
    private CatalogQueryApi catalogQueryApi;

    @Test
    void concurrentCheckout_DoesNotOversell() throws InterruptedException {
        String vendorId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        String sku = "CONCURRENCY-SKU";

        when(catalogQueryApi.getVariantInfo(anyString(), anyString())).thenReturn(
                Optional.of(new CatalogQueryApi.ProductVariantInfo(productId, vendorId, sku, "Test Product", new BigDecimal("10.00"), "USD"))
        );
        when(catalogQueryApi.isProductVariantActive(anyString(), anyString())).thenReturn(true);

        inventoryUseCases.createInventoryItem(vendorId, sku);
        inventoryUseCases.adjustInventory(UUID.fromString(productId), 10, "init"); // Only 10 available

        int threadCount = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final String customerId = "cust-" + i;
            final String idempotencyKey = UUID.randomUUID().toString();
            
            cartUseCases.addItemToCart(customerId, vendorId, sku, 1);
            
            tasks.add(() -> {
                try {
                    checkoutUseCases.checkout(customerId, idempotencyKey, null);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            });
        }

        List<Future<Boolean>> results = executorService.invokeAll(tasks);
        
        int successCount = 0;
        for (Future<Boolean> r : results) {
            try {
                if (r.get()) successCount++;
            } catch (Exception e) {}
        }
        
        executorService.shutdown();

        // Should exactly equal available inventory
        assertEquals(10, successCount);

        var row = jdbcClient.sql("SELECT * FROM inventory.inventory_items WHERE product_id = :id")
                .param("id", UUID.fromString(productId))
                .query().singleRow();
        assertEquals(10, row.get("on_hand_quantity"));
        assertEquals(10, row.get("reserved_quantity"));
        assertEquals(0, row.get("available_quantity"));
    }
}
