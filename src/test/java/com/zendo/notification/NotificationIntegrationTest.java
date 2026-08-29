package com.zendo.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zendo.TestcontainersConfiguration;
import com.zendo.notification.api.NotificationQueryApi;
import com.zendo.notification.infrastructure.persistence.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class NotificationIntegrationTest {

    @Container
    static RabbitMQContainer rabbitMQContainer = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.12-management"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQContainer::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQContainer::getAdminPassword);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private NotificationQueryApi notificationQueryApi;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateNotificationOnOrderPlacedEvent() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String customerId = "cust-notif-" + UUID.randomUUID();
        String orderId = UUID.randomUUID().toString();

        Map<String, Object> event = Map.of(
                "eventId", eventId,
                "eventType", "OrderPlaced",
                "occurredAt", "2026-08-26T12:00:00Z",
                "orderId", orderId,
                "customerId", customerId,
                "cartId", UUID.randomUUID().toString(),
                "totalAmount", "150.00",
                "currency", "USD"
        );

        String payload = objectMapper.writeValueAsString(event);
        rabbitTemplate.convertAndSend("zendo.topic", "order.events", payload);

        // Wait for consumer to process
        Thread.sleep(2000);

        List<NotificationQueryApi.NotificationSummary> notifications =
                notificationQueryApi.getNotificationsForUser(customerId);

        // Now we expect 2 notifications because Payment module also listens to OrderPlaced
        // and produces PaymentAuthorized which NotificationPaymentEventConsumer listens to
        // and assigns to the same customerId.
        assertEquals(2, notifications.size());
        assertTrue(notifications.stream().anyMatch(n -> n.type().equals("ORDER_CONFIRMED")));
        assertTrue(notifications.stream().anyMatch(n -> n.type().equals("PAYMENT_RECEIVED")));
        assertTrue(notifications.stream().anyMatch(n -> n.message().contains(orderId)));

        // Verify idempotency tracking
        assertTrue(processedEventRepository.existsById(eventId));
    }

    @Test
    void shouldNotCreateDuplicateNotificationOnDuplicateEvent() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String customerId = "cust-dedup-" + UUID.randomUUID();
        String orderId = UUID.randomUUID().toString();

        Map<String, Object> event = Map.of(
                "eventId", eventId,
                "eventType", "OrderPlaced",
                "occurredAt", "2026-08-26T12:00:00Z",
                "orderId", orderId,
                "customerId", customerId,
                "cartId", UUID.randomUUID().toString(),
                "totalAmount", "50.00",
                "currency", "USD"
        );

        String payload = objectMapper.writeValueAsString(event);

        // Send same event twice
        rabbitTemplate.convertAndSend("zendo.topic", "order.events", payload);
        Thread.sleep(1500);
        rabbitTemplate.convertAndSend("zendo.topic", "order.events", payload);
        Thread.sleep(1500);

        List<NotificationQueryApi.NotificationSummary> notifications =
                notificationQueryApi.getNotificationsForUser(customerId);

        // Should still be exactly 2 (Order Confirmed + Payment Authorized)
        assertEquals(2, notifications.size());
    }

    @Test
    void shouldCreateNotificationOnPaymentAuthorizedEvent() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        String customerId = "cust-pay-" + UUID.randomUUID();

        Map<String, Object> event = Map.of(
                "eventId", eventId,
                "eventType", "PaymentAuthorized",
                "occurredAt", "2026-08-26T12:00:00Z",
                "paymentId", UUID.randomUUID().toString(),
                "orderId", orderId,
                "customerId", customerId,
                "amount", "200.00",
                "currency", "USD"
        );

        String payload = objectMapper.writeValueAsString(event);
        rabbitTemplate.convertAndSend("zendo.topic", "payment.events", payload);

        Thread.sleep(2000);

        List<NotificationQueryApi.NotificationSummary> notifications =
                notificationQueryApi.getNotificationsForUser(customerId);

        assertEquals(1, notifications.size());
        assertEquals("PAYMENT_RECEIVED", notifications.get(0).type());
        assertEquals("SENT", notifications.get(0).status());
        assertTrue(notifications.get(0).message().contains("200.00"));
    }

    @Test
    void shouldCreateNotificationOnPaymentFailedEvent() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        String customerId = "cust-fail-" + UUID.randomUUID();

        Map<String, Object> event = Map.of(
                "eventId", eventId,
                "eventType", "PaymentFailed",
                "occurredAt", "2026-08-26T12:00:00Z",
                "paymentId", UUID.randomUUID().toString(),
                "orderId", orderId,
                "customerId", customerId,
                "reason", "Insufficient funds"
        );

        String payload = objectMapper.writeValueAsString(event);
        rabbitTemplate.convertAndSend("zendo.topic", "payment.events", payload);

        Thread.sleep(2000);

        List<NotificationQueryApi.NotificationSummary> notifications =
                notificationQueryApi.getNotificationsForUser(customerId);

        assertEquals(1, notifications.size());
        assertEquals("PAYMENT_FAILED", notifications.get(0).type());
        assertTrue(notifications.get(0).message().contains("Insufficient funds"));
    }
}
