package com.zendo.cart;

import com.zendo.cart.application.CartUseCases;
import com.zendo.cart.domain.Cart;
import com.zendo.cart.domain.CartRepository;
import com.zendo.cart.domain.CartStatus;
import com.zendo.order.domain.events.OrderPlaced;
import com.zendo.shared.outbox.OutboxEvent;
import com.zendo.shared.outbox.OutboxEventStore;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
public class CartClearingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    static RabbitMQContainer rabbitMQContainer = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.12-management"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQContainer::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQContainer::getAdminPassword);
    }

    @Autowired
    private CartUseCases cartUseCases;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private com.zendo.shared.messaging.EventSigner eventSigner;

    @Test
    void shouldClearCartWhenOrderPlacedEventReceived() throws InterruptedException {
        String customerId = "customer-" + UUID.randomUUID().toString();
        
        // 1. Create an active cart and add an item so it is not empty
        Cart cart = cartUseCases.getOrCreateActiveCart(customerId);
        assertEquals(CartStatus.ACTIVE, cart.getStatus());
        cart.addItem(UUID.randomUUID(), UUID.randomUUID(), "SKU-TEST-123", 1);
        cartRepository.save(cart);
        
        // 2. Simulate OrderPlaced event
        String jsonPayload = """
        {
            "eventId": "%s",
            "occurredAt": "%s",
            "orderId": "%s",
            "customerId": "%s",
            "cartId": "%s",
            "totalAmount": 100.00,
            "currency": "USD",
            "eventType": "OrderPlaced"
        }
        """.formatted(UUID.randomUUID(), Instant.now().toString(), UUID.randomUUID(), customerId, cart.getId().toString());

        String signature = eventSigner.sign(jsonPayload);
        rabbitTemplate.convertAndSend("zendo.topic", "order.events", jsonPayload, msg -> {
            msg.getMessageProperties().setHeader("X-Event-Signature", signature);
            return msg;
        });
        
        // 3. Wait for consumer to process
        Thread.sleep(1500);
        
        // 4. Verify cart is checked out
        Optional<Cart> activeCart = cartRepository.findActiveCartByCustomerId(customerId);
        assertTrue(activeCart.isEmpty(), "Active cart should be empty (checked out)");
    }
}
