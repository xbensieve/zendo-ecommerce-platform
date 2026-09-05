package com.zendo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zendo.order.application.OrderUseCases;
import com.zendo.order.domain.OrderRepository;
import com.zendo.order.infrastructure.messaging.PaymentResultRabbitConsumer;
import com.zendo.shared.messaging.EventSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RabbitMqEventSecurityTest {

    @Mock
    private OrderUseCases orderUseCases;

    @Mock
    private OrderRepository orderRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EventSigner eventSigner = new EventSigner("test-secret-key-32-bytes-minimum!!");

    private PaymentResultRabbitConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentResultRabbitConsumer(orderUseCases, orderRepository, objectMapper, eventSigner);
    }

    @Test
    @DisplayName("Unsigned RabbitMQ event is rejected with AmqpRejectAndDontRequeueException")
    void unsignedEvent_isRejected() {
        String payload = """
            {
                "eventId": "%s",
                "eventType": "PaymentAuthorized",
                "orderId": "%s"
            }
        """.formatted(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> consumer.handle(payload, null))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("Untrusted or unsigned event signature");

        verifyNoInteractions(orderUseCases);
    }

    @Test
    @DisplayName("Tampered payload with valid original signature is rejected")
    void tamperedPayload_isRejected() {
        String orderId = UUID.randomUUID().toString();
        String originalPayload = """
            {"eventId":"%s","eventType":"PaymentAuthorized","orderId":"%s","amount":10.00}
        """.formatted(UUID.randomUUID(), orderId);

        String validSignature = eventSigner.sign(originalPayload);

        // Attacker alters payload to change amount or target orderId
        String tamperedPayload = """
            {"eventId":"%s","eventType":"PaymentAuthorized","orderId":"%s","amount":9999.00}
        """.formatted(UUID.randomUUID(), orderId);

        assertThatThrownBy(() -> consumer.handle(tamperedPayload, validSignature))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("Untrusted or unsigned event signature");

        verifyNoInteractions(orderUseCases);
    }

    @Test
    @DisplayName("Validly signed event is accepted and updates business state")
    void validlySignedEvent_isAccepted() {
        UUID eventId = UUID.randomUUID();
        String orderId = UUID.randomUUID().toString();
        String payload = """
            {"eventId":"%s","eventType":"PaymentAuthorized","orderId":"%s","amount":"100.00","currency":"USD","occurredAt":"%s"}
        """.formatted(eventId, orderId, java.time.Instant.now());

        String signature = eventSigner.sign(payload);
        when(orderRepository.checkAndSaveIdempotencyKey("payment_event_" + eventId)).thenReturn(true);

        assertThatNoException().isThrownBy(() -> consumer.handle(payload, signature));
        verify(orderUseCases).markPaymentAuthorized(eq(orderId), eq(new java.math.BigDecimal("100.00")), eq("USD"), any());
    }

    @Test
    @DisplayName("Duplicate event replay is safely ignored via idempotency check")
    void duplicateEventReplay_isSafelyIgnored() {
        UUID eventId = UUID.randomUUID();
        String orderId = UUID.randomUUID().toString();
        String payload = """
            {"eventId":"%s","eventType":"PaymentAuthorized","orderId":"%s","amount":"100.00","currency":"USD","occurredAt":"%s"}
        """.formatted(eventId, orderId, java.time.Instant.now());

        String signature = eventSigner.sign(payload);

        // First execution: key is new
        when(orderRepository.checkAndSaveIdempotencyKey("payment_event_" + eventId)).thenReturn(true);
        consumer.handle(payload, signature);
        verify(orderUseCases, times(1)).markPaymentAuthorized(eq(orderId), eq(new java.math.BigDecimal("100.00")), eq("USD"), any());

        // Replay: key already exists
        when(orderRepository.checkAndSaveIdempotencyKey("payment_event_" + eventId)).thenReturn(false);
        consumer.handle(payload, signature);

        // Verify markPaymentAuthorized was NOT called a second time
        verify(orderUseCases, times(1)).markPaymentAuthorized(eq(orderId), eq(new java.math.BigDecimal("100.00")), eq("USD"), any());
    }
}
