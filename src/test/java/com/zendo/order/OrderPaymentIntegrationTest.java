package com.zendo.order;

import com.zendo.order.application.CheckoutUseCases;
import com.zendo.order.domain.OrderRepository;
import com.zendo.order.domain.OrderStatus;
import com.zendo.order.domain.ParentOrder;
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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
public class OrderPaymentIntegrationTest {

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
    private OrderRepository orderRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private org.springframework.jdbc.core.simple.JdbcClient jdbcClient;

    @Autowired
    private com.zendo.shared.messaging.EventSigner eventSigner;

    @Test
    void shouldUpdateOrderWhenPaymentAuthorized() throws InterruptedException {
        UUID orderId = UUID.randomUUID();
        String customerId = "cust-123";

        // Insert a dummy order directly to DB to bypass domain complexity for testing event handling
        jdbcClient.sql("INSERT INTO order_ctx.parent_orders (id, customer_id, status, currency, total_amount) VALUES (:id, :customerId, :status, :currency, :total)")
                .param("id", orderId)
                .param("customerId", customerId)
                .param("status", OrderStatus.PAYMENT_PENDING.name())
                .param("currency", "USD")
                .param("total", new java.math.BigDecimal("100.00"))
                .update();

        // Must insert child order to satisfy ParentOrder.verifyInvariants
        jdbcClient.sql("INSERT INTO order_ctx.child_orders (id, parent_order_id, vendor_id, total_amount, currency) VALUES (:id, :parentId, :vendorId, :total, :currency)")
                .param("id", UUID.randomUUID())
                .param("parentId", orderId)
                .param("vendorId", UUID.randomUUID())
                .param("total", new java.math.BigDecimal("100.00"))
                .param("currency", "USD")
                .update();
        
        // Publish PaymentAuthorized event
        String jsonPayload = """
        {
            "eventId": "%s",
            "occurredAt": "%s",
            "paymentId": "%s",
            "orderId": "%s",
            "amount": 100.00,
            "currency": "USD",
            "eventType": "PaymentAuthorized"
        }
        """.formatted(UUID.randomUUID(), Instant.now().toString(), UUID.randomUUID(), orderId);

        String signature = eventSigner.sign(jsonPayload);
        rabbitTemplate.convertAndSend("zendo.topic", "payment.events", jsonPayload, msg -> {
            msg.getMessageProperties().setHeader("X-Event-Signature", signature);
            return msg;
        });
        
        // Wait for consumer
        Thread.sleep(1500);
        
        // Verify Order status updated
        String status = jdbcClient.sql("SELECT status FROM order_ctx.parent_orders WHERE id = :id")
                .param("id", orderId)
                .query(String.class)
                .single();
                
        assertEquals(OrderStatus.PAYMENT_AUTHORIZED.name(), status);
    }
}
