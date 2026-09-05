package com.zendo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zendo.order.application.OrderUseCases;
import com.zendo.order.domain.*;
import com.zendo.order.infrastructure.messaging.PaymentResultRabbitConsumer;
import com.zendo.shared.messaging.EventSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvalidPaymentStateTransitionTest {

    private OrderRepository orderRepository;
    private OrderUseCases orderUseCases;
    private PaymentResultRabbitConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EventSigner eventSigner = new EventSigner("test-secret-key-32-bytes-minimum!!");

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        when(orderRepository.checkAndSaveIdempotencyKey(anyString())).thenReturn(true);
        orderUseCases = new OrderUseCases(orderRepository);
        consumer = new PaymentResultRabbitConsumer(orderUseCases, orderRepository, objectMapper, eventSigner);
    }

    private ParentOrder createTestOrder(OrderStatus status) {
        UUID orderId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        OrderItem item = new OrderItem(
                itemId, UUID.randomUUID(), "SKU-1", "Product 1",
                new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("50.00"), null, 1
        );
        ChildOrder childOrder = new ChildOrder(UUID.randomUUID(), vendorId, "USD", List.of(item));
        return new ParentOrder(orderId, "cust-1", status, "USD", List.of(childOrder), new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("Should reject PaymentAuthorized when order is in CANCELLED state")
    void shouldRejectPaymentAuthorizedOnCancelledOrder() {
        ParentOrder order = createTestOrder(OrderStatus.CANCELLED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        String payload = String.format(
                "{\"eventId\":\"%s\",\"eventType\":\"PaymentAuthorized\",\"orderId\":\"%s\",\"amount\":\"50.00\",\"currency\":\"USD\",\"occurredAt\":\"%s\"}",
                UUID.randomUUID(), order.getId(), Instant.now()
        );
        String signature = eventSigner.sign(payload);

        AmqpRejectAndDontRequeueException ex = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumer.handle(payload, signature)
        );
        assertTrue(ex.getMessage().contains("Illegal order state transition"));
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    @DisplayName("Should reject PaymentFailed after order was already PAYMENT_AUTHORIZED")
    void shouldRejectPaymentFailedAfterAuthorized() {
        ParentOrder order = createTestOrder(OrderStatus.PAYMENT_AUTHORIZED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        String payload = String.format(
                "{\"eventId\":\"%s\",\"eventType\":\"PaymentFailed\",\"orderId\":\"%s\",\"occurredAt\":\"%s\"}",
                UUID.randomUUID(), order.getId(), Instant.now()
        );
        String signature = eventSigner.sign(payload);

        AmqpRejectAndDontRequeueException ex = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumer.handle(payload, signature)
        );
        assertTrue(ex.getMessage().contains("Illegal order state transition"));
        assertEquals(OrderStatus.PAYMENT_AUTHORIZED, order.getStatus());
    }

    @Test
    @DisplayName("Should reject cancel() when order is already PAYMENT_AUTHORIZED")
    void shouldRejectCancelOnAuthorizedOrder() {
        ParentOrder order = createTestOrder(OrderStatus.PAYMENT_AUTHORIZED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        OrderException ex = assertThrows(
                OrderException.class,
                () -> orderUseCases.cancelOrder(order.getId().toString())
        );
        assertTrue(ex.getMessage().contains("Cannot cancel order from status: PAYMENT_AUTHORIZED"));
    }

    @Test
    @DisplayName("Domain transition should be idempotent for duplicate PaymentAuthorized calls")
    void shouldBeIdempotentForDuplicatePaymentAuthorized() {
        ParentOrder order = createTestOrder(OrderStatus.PAYMENT_PENDING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        order.markPaymentAuthorized();
        assertEquals(OrderStatus.PAYMENT_AUTHORIZED, order.getStatus());

        // Second call must be idempotent (no exception)
        assertDoesNotThrow(() -> order.markPaymentAuthorized());
        assertEquals(OrderStatus.PAYMENT_AUTHORIZED, order.getStatus());
    }
}
