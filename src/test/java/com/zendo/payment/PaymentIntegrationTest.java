package com.zendo.payment;

import com.zendo.payment.application.PaymentUseCases;
import com.zendo.payment.domain.PaymentRepository;
import com.zendo.payment.domain.PaymentStatus;
import com.zendo.payment.domain.PaymentTransaction;
import org.junit.jupiter.api.Test;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
public class PaymentIntegrationTest {

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
    private PaymentUseCases paymentUseCases;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void shouldAuthorizePaymentSuccessfully() {
        String orderId = UUID.randomUUID().toString();
        String idempotencyKey = "key-" + orderId;

        paymentUseCases.authorizePayment(orderId, "customer-id-test", new BigDecimal("100.00"), "USD", idempotencyKey);

        Optional<PaymentTransaction> tx = paymentRepository.findByOrderId(UUID.fromString(orderId));
        assertTrue(tx.isPresent());
        assertEquals(PaymentStatus.AUTHORIZED, tx.get().getStatus());
        assertNotNull(tx.get().getGatewayReference());
    }

    @Test
    void shouldBeIdempotentOnDuplicateAuthorization() {
        String orderId = UUID.randomUUID().toString();
        String idempotencyKey = "idem-" + orderId;

        paymentUseCases.authorizePayment(orderId, "customer-id-test", new BigDecimal("150.00"), "USD", idempotencyKey);
        
        // This should just return without error because of the idempotency key
        paymentUseCases.authorizePayment(orderId, "customer-id-test", new BigDecimal("150.00"), "USD", idempotencyKey);

        Optional<PaymentTransaction> tx = paymentRepository.findByOrderId(UUID.fromString(orderId));
        assertTrue(tx.isPresent());
        assertEquals(PaymentStatus.AUTHORIZED, tx.get().getStatus());
    }

    @Test
    void shouldFailPaymentIfGatewayRejects() {
        String orderId = UUID.randomUUID().toString();
        String idempotencyKey = "idem-fail-" + orderId;

        // Our FakePaymentGatewayAdapter fails on 0 amount
        paymentUseCases.authorizePayment(orderId, "customer-id-test", BigDecimal.ZERO, "USD", idempotencyKey);

        Optional<PaymentTransaction> tx = paymentRepository.findByOrderId(UUID.fromString(orderId));
        assertTrue(tx.isPresent());
        assertEquals(PaymentStatus.FAILED, tx.get().getStatus());
        assertNull(tx.get().getGatewayReference());
    }
}
