package com.zendo;

import com.zendo.cart.api.CartQueryApi;
import com.zendo.cart.application.CartUseCases;
import com.zendo.catalog.api.CatalogQueryApi;
import com.zendo.inventory.application.InventoryUseCases;
import com.zendo.order.application.CheckoutUseCases;
import com.zendo.order.domain.OrderException;
import com.zendo.order.domain.ParentOrder;
import com.zendo.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OrderCheckoutIntegrationTest {

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
    void checkout_SuccessfulFlow() throws InterruptedException {
        String customerId = "cust-checkout-123";
        String vendorId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        String sku = "CHECKOUT-SKU";

        // Setup Catalog Mock
        when(catalogQueryApi.getVariantInfo(anyString(), anyString())).thenReturn(
                Optional.of(new CatalogQueryApi.ProductVariantInfo(productId, vendorId, sku, "Test Product", new BigDecimal("10.00"), "USD"))
        );
        when(catalogQueryApi.isProductVariantActive(anyString(), anyString())).thenReturn(true);

        // Setup Inventory (10 units)
        inventoryUseCases.createInventoryItem(vendorId, sku);
        inventoryUseCases.adjustInventory(UUID.fromString(productId), 10, "init");

        // Setup Cart (add 2 units)
        cartUseCases.addItemToCart(customerId, vendorId, sku, 2);

        // Checkout
        String idempotencyKey = UUID.randomUUID().toString();
        ParentOrder order = checkoutUseCases.checkout(customerId, idempotencyKey, null);
        assertNotNull(order);
        assertEquals(OrderStatus.PAYMENT_PENDING, order.getStatus());
        assertEquals(new BigDecimal("20.00"), order.getTotalAmount());
        assertEquals(1, order.getChildOrders().size());
        
        // Verify Inventory Reserved (10 - 2 = 8 available)
        var row = jdbcClient.sql("SELECT * FROM inventory.inventory_items WHERE product_id = :id")
                .param("id", UUID.fromString(productId))
                .query().singleRow();
        assertEquals(10, row.get("on_hand_quantity"));
        assertEquals(2, row.get("reserved_quantity"));
        assertEquals(8, row.get("available_quantity"));

        // Note: The cart is cleared asynchronously via RabbitMQ Event (OrderPlaced).
        // For this unit test, since we might not have the OutboxRelay and RabbitMQ fully hooked up 
        // to consume fast enough before the assertion, we just verify the order was created.
        
        // Wait briefly to allow async processing
        Thread.sleep(3000);
        
        // Either the cart is checked out (0 active) or it's still being processed.
        // We will just verify it doesn't throw and Order is PAYMENT_PENDING
        assertEquals(OrderStatus.PAYMENT_PENDING, order.getStatus());
        var cartState = cartUseCases.getOrCreateActiveCart(customerId);
        assertEquals(0, cartState.getItems().size()); // getting active cart should give a new empty one
        
        // Verify Outbox
        var outboxCount = jdbcClient.sql("SELECT COUNT(*) FROM order_ctx.outbox_events WHERE aggregate_id = :id")
                .param("id", order.getId().toString())
                .query(Integer.class).single();
        assertEquals(1, outboxCount);
    }
    
    @Test
    void checkout_FailsIfIdempotencyKeyReused() {
        String customerId = "cust-idem-123";
        String vendorId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        String sku = "IDEM-SKU";

        // Setup Catalog Mock
        when(catalogQueryApi.getVariantInfo(anyString(), anyString())).thenReturn(
                Optional.of(new CatalogQueryApi.ProductVariantInfo(productId, vendorId, sku, "Test Product", new BigDecimal("10.00"), "USD"))
        );
        when(catalogQueryApi.isProductVariantActive(anyString(), anyString())).thenReturn(true);

        inventoryUseCases.createInventoryItem(vendorId, sku);
        inventoryUseCases.adjustInventory(UUID.fromString(productId), 10, "init");
        cartUseCases.addItemToCart(customerId, vendorId, sku, 2);

        String idempotencyKey = UUID.randomUUID().toString();
        checkoutUseCases.checkout(customerId, idempotencyKey, null);
        
        // Add item to cart again so cart is not empty
        cartUseCases.addItemToCart(customerId, vendorId, sku, 1);
        
        // Second checkout with same key should fail
        assertThrows(OrderException.class, () -> checkoutUseCases.checkout(customerId, idempotencyKey, null));
    }
    
    @Test
    void checkout_FailsIfInventoryInsufficient() {
        String customerId = "cust-insuf-123";
        String vendorId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        String sku = "INSUF-SKU";

        when(catalogQueryApi.getVariantInfo(anyString(), anyString())).thenReturn(
                Optional.of(new CatalogQueryApi.ProductVariantInfo(productId, vendorId, sku, "Test Product", new BigDecimal("10.00"), "USD"))
        );
        when(catalogQueryApi.isProductVariantActive(anyString(), anyString())).thenReturn(true);

        inventoryUseCases.createInventoryItem(vendorId, sku);
        inventoryUseCases.adjustInventory(UUID.fromString(productId), 5, "init");
        
        cartUseCases.addItemToCart(customerId, vendorId, sku, 10); // Ask for 10, have 5

        String idempotencyKey = UUID.randomUUID().toString();
        
        assertThrows(Exception.class, () -> checkoutUseCases.checkout(customerId, idempotencyKey, null));
        
        // Verify Inventory is NOT reserved
        var row = jdbcClient.sql("SELECT * FROM inventory.inventory_items WHERE product_id = :id")
                .param("id", UUID.fromString(productId))
                .query().singleRow();
        assertEquals(5, row.get("on_hand_quantity"));
        assertEquals(0, row.get("reserved_quantity"));
    }
    @Autowired
    private com.zendo.promotion.domain.CouponRepository couponRepository;

    @Test
    void checkout_WithCoupon_ShouldCalculatePricesCorrectly() {
        String customerId = "cust-coupon-123";
        String vendorId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        String sku = "COUPON-SKU";

        when(catalogQueryApi.getVariantInfo(anyString(), anyString())).thenReturn(
                Optional.of(new CatalogQueryApi.ProductVariantInfo(productId, vendorId, sku, "Test Product", new BigDecimal("100.00"), "USD"))
        );
        when(catalogQueryApi.isProductVariantActive(anyString(), anyString())).thenReturn(true);

        inventoryUseCases.createInventoryItem(vendorId, sku);
        inventoryUseCases.adjustInventory(UUID.fromString(productId), 10, "init");
        cartUseCases.addItemToCart(customerId, vendorId, sku, 2);

        String idempotencyKey = UUID.randomUUID().toString();
        String couponCode = "HALF_OFF";

        // Save a coupon
        couponRepository.save(new com.zendo.promotion.domain.Coupon(
                UUID.randomUUID(),
                couponCode,
                java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS),
                java.time.Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS),
                new com.zendo.promotion.domain.DiscountRule(com.zendo.promotion.domain.DiscountType.PERCENTAGE, new BigDecimal("50")),
                com.zendo.promotion.domain.EligibilityScope.global(),
                100
        ));
        
        ParentOrder order = checkoutUseCases.checkout(customerId, idempotencyKey, couponCode);
        
        assertNotNull(order);
        assertEquals(1, order.getChildOrders().size());
        assertEquals(1, order.getChildOrders().get(0).getItems().size());
        
        com.zendo.order.domain.OrderItem item = order.getChildOrders().get(0).getItems().get(0);
        
        assertEquals(0, new BigDecimal("100.00").compareTo(item.getOriginalUnitPrice()));
        assertEquals(0, new BigDecimal("50.00").compareTo(item.getAppliedDiscount()));
        assertEquals(0, new BigDecimal("50.00").compareTo(item.getFinalUnitPrice()));
        assertEquals("COUPON:HALF_OFF", item.getPromotionRef());
        
        // Ensure total amount is 2 * 50.00 = 100.00
        assertEquals(0, new BigDecimal("100.00").compareTo(order.getTotalAmount()));
    }
}
