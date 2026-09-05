package com.zendo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zendo.order.application.OrderUseCases;
import com.zendo.order.domain.OrderRepository;
import com.zendo.order.infrastructure.messaging.PaymentResultRabbitConsumer;
import com.zendo.shared.messaging.EventSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class EventReplayFreshnessTest {

    private OrderUseCases orderUseCases;
    private OrderRepository orderRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EventSigner eventSigner = new EventSigner("test-secret-key-32-bytes-minimum!!");
    private PaymentResultRabbitConsumer consumer;

    @BeforeEach
    void setUp() {
        orderUseCases = mock(OrderUseCases.class);
        orderRepository = mock(OrderRepository.class);
        when(orderRepository.checkAndSaveIdempotencyKey(anyString())).thenReturn(true);
        consumer = new PaymentResultRabbitConsumer(orderUseCases, orderRepository, objectMapper, eventSigner);
    }

    @Test
    @DisplayName("P1-01: Reject missing occurredAt timestamp")
    void shouldRejectMissingOccurredAt() {
        String eventId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();

        String payload = String.format(
                "{\"eventId\":\"%s\",\"eventType\":\"PaymentAuthorized\",\"orderId\":\"%s\",\"amount\":\"100.00\",\"currency\":\"USD\"}",
                eventId, orderId
        );
        String signature = eventSigner.sign(payload);

        AmqpRejectAndDontRequeueException ex = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumer.handle(payload, signature)
        );
        assertTrue(ex.getMessage().contains("occurredAt"), "Expected rejection due to missing occurredAt timestamp: " + ex.getMessage());
        verify(orderUseCases, never()).markPaymentAuthorized(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("P1-01: Reject malformed occurredAt timestamp")
    void shouldRejectMalformedOccurredAt() {
        String eventId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();

        String payload = String.format(
                "{\"eventId\":\"%s\",\"eventType\":\"PaymentAuthorized\",\"orderId\":\"%s\",\"amount\":\"100.00\",\"currency\":\"USD\",\"occurredAt\":\"not-a-valid-timestamp\"}",
                eventId, orderId
        );
        String signature = eventSigner.sign(payload);

        AmqpRejectAndDontRequeueException ex = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumer.handle(payload, signature)
        );
        assertTrue(ex.getMessage().contains("Invalid occurredAt") || ex.getMessage().contains("timestamp"));
        verify(orderUseCases, never()).markPaymentAuthorized(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("P1-01: Reject stale event older than maximum age (> 24 hours)")
    void shouldRejectExpiredReplayEvent() {
        String eventId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        Instant expiredTime = Instant.now().minusSeconds(100000); // ~27 hours ago

        String payload = String.format(
                "{\"eventId\":\"%s\",\"eventType\":\"PaymentAuthorized\",\"orderId\":\"%s\",\"amount\":\"100.00\",\"currency\":\"USD\",\"occurredAt\":\"%s\"}",
                eventId, orderId, expiredTime
        );
        String signature = eventSigner.sign(payload);

        AmqpRejectAndDontRequeueException ex = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumer.handle(payload, signature)
        );
        assertTrue(ex.getMessage().contains("freshness window") || ex.getMessage().contains("expired"));
        verify(orderUseCases, never()).markPaymentAuthorized(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("P1-01: Reject future event exceeding clock skew tolerance (> 5 min)")
    void shouldRejectFutureTimestampEvent() {
        String eventId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        Instant futureTime = Instant.now().plusSeconds(600); // 10 minutes in future

        String payload = String.format(
                "{\"eventId\":\"%s\",\"eventType\":\"PaymentAuthorized\",\"orderId\":\"%s\",\"amount\":\"100.00\",\"currency\":\"USD\",\"occurredAt\":\"%s\"}",
                eventId, orderId, futureTime
        );
        String signature = eventSigner.sign(payload);

        AmqpRejectAndDontRequeueException ex = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumer.handle(payload, signature)
        );
        assertTrue(ex.getMessage().contains("clock skew tolerance") || ex.getMessage().contains("future"));
        verify(orderUseCases, never()).markPaymentAuthorized(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("P1-01: Accept fresh event with valid signature")
    void shouldAcceptFreshEvent() {
        String eventId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        Instant freshTime = Instant.now();

        String payload = String.format(
                "{\"eventId\":\"%s\",\"eventType\":\"PaymentAuthorized\",\"orderId\":\"%s\",\"amount\":\"100.00\",\"currency\":\"USD\",\"occurredAt\":\"%s\"}",
                eventId, orderId, freshTime
        );
        String signature = eventSigner.sign(payload);

        assertDoesNotThrow(() -> consumer.handle(payload, signature));
        verify(orderUseCases).markPaymentAuthorized(eq(orderId), eq(new BigDecimal("100.00")), eq("USD"), any());
    }

    @Test
    @DisplayName("P1-01: Duplicate event replay is safely skipped via idempotency key")
    void shouldSkipDuplicateEvent() {
        String eventId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        Instant freshTime = Instant.now();

        String payload = String.format(
                "{\"eventId\":\"%s\",\"eventType\":\"PaymentAuthorized\",\"orderId\":\"%s\",\"amount\":\"100.00\",\"currency\":\"USD\",\"occurredAt\":\"%s\"}",
                eventId, orderId, freshTime
        );
        String signature = eventSigner.sign(payload);

        when(orderRepository.checkAndSaveIdempotencyKey("payment_event_" + eventId)).thenReturn(false);

        assertDoesNotThrow(() -> consumer.handle(payload, signature));
        verify(orderUseCases, never()).markPaymentAuthorized(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("P1-01: Reject invalid signature with fresh timestamp")
    void shouldRejectInvalidSignatureWithFreshTimestamp() {
        String eventId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();

        String payload = String.format(
                "{\"eventId\":\"%s\",\"eventType\":\"PaymentAuthorized\",\"orderId\":\"%s\",\"amount\":\"100.00\",\"currency\":\"USD\",\"occurredAt\":\"%s\"}",
                eventId, orderId, Instant.now()
        );

        AmqpRejectAndDontRequeueException ex = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumer.handle(payload, "invalid-hmac-signature")
        );
        assertTrue(ex.getMessage().contains("Untrusted or unsigned event signature"));
        verify(orderUseCases, never()).markPaymentAuthorized(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("P1-01: Reject valid signature with stale timestamp")
    void shouldRejectValidSignatureWithStaleTimestamp() {
        String eventId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        Instant staleTime = Instant.now().minusSeconds(86400 * 3); // 3 days ago

        String payload = String.format(
                "{\"eventId\":\"%s\",\"eventType\":\"PaymentAuthorized\",\"orderId\":\"%s\",\"amount\":\"100.00\",\"currency\":\"USD\",\"occurredAt\":\"%s\"}",
                eventId, orderId, staleTime
        );
        String validSignature = eventSigner.sign(payload);

        AmqpRejectAndDontRequeueException ex = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumer.handle(payload, validSignature)
        );
        assertTrue(ex.getMessage().contains("freshness window") || ex.getMessage().contains("expired"));
        verify(orderUseCases, never()).markPaymentAuthorized(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("P1-01 ADVERSARIAL: Attempt freshness bypass by stripping occurredAt from old captured event")
    void adversarial_bypassFreshnessByStrippingOccurredAt_isBlocked() {
        String eventId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();

        // Attacker creates a signed payload without occurredAt hoping to bypass freshness check
        String strippedPayload = String.format(
                "{\"eventId\":\"%s\",\"eventType\":\"PaymentAuthorized\",\"orderId\":\"%s\",\"amount\":\"100.00\",\"currency\":\"USD\"}",
                eventId, orderId
        );
        String validSignature = eventSigner.sign(strippedPayload);

        AmqpRejectAndDontRequeueException ex = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumer.handle(strippedPayload, validSignature)
        );
        assertTrue(ex.getMessage().contains("occurredAt"), "Must reject even if HMAC is valid because occurredAt is mandatory");
        verify(orderUseCases, never()).markPaymentAuthorized(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("P1-01 ADVERSARIAL: Manipulating occurredAt on validly signed stale event invalidates HMAC")
    void adversarial_manipulateTimestampOnOldEvent_invalidatesHmac() {
        String eventId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        Instant staleTime = Instant.now().minusSeconds(86400 * 2);

        String originalPayload = String.format(
                "{\"eventId\":\"%s\",\"eventType\":\"PaymentAuthorized\",\"orderId\":\"%s\",\"amount\":\"100.00\",\"currency\":\"USD\",\"occurredAt\":\"%s\"}",
                eventId, orderId, staleTime
        );
        String originalSignature = eventSigner.sign(originalPayload);

        // Attacker replaces old timestamp with fresh timestamp but cannot forge signature
        String tamperedPayload = String.format(
                "{\"eventId\":\"%s\",\"eventType\":\"PaymentAuthorized\",\"orderId\":\"%s\",\"amount\":\"100.00\",\"currency\":\"USD\",\"occurredAt\":\"%s\"}",
                eventId, orderId, Instant.now()
        );

        AmqpRejectAndDontRequeueException ex = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumer.handle(tamperedPayload, originalSignature)
        );
        assertTrue(ex.getMessage().contains("Untrusted or unsigned event signature"));
        verify(orderUseCases, never()).markPaymentAuthorized(anyString(), any(), any(), any());
    }
}
