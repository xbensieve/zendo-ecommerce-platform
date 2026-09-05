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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class PaymentFinancialIntegrityTest {

    private OrderRepository orderRepository;
    private OrderUseCases orderUseCases;
    private PaymentResultRabbitConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EventSigner eventSigner = new EventSigner("test-secret-key-32-bytes-minimum!!");

    private ParentOrder testOrder;
    private final BigDecimal ORDER_AMOUNT = new BigDecimal("150.00");
    private final String ORDER_CURRENCY = "USD";
    private final String CUSTOMER_ID = "customer-123";

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        when(orderRepository.checkAndSaveIdempotencyKey(anyString())).thenReturn(true);
        orderUseCases = new OrderUseCases(orderRepository);
        consumer = new PaymentResultRabbitConsumer(orderUseCases, orderRepository, objectMapper, eventSigner);

        testOrder = createTestOrder(OrderStatus.PAYMENT_PENDING, ORDER_AMOUNT, ORDER_CURRENCY, CUSTOMER_ID);
        when(orderRepository.findById(testOrder.getId())).thenReturn(Optional.of(testOrder));
    }

    private ParentOrder createTestOrder(OrderStatus status, BigDecimal totalAmount, String currency, String customerId) {
        UUID orderId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        OrderItem item = new OrderItem(
                itemId, UUID.randomUUID(), "SKU-TEST", "Product Test",
                totalAmount, BigDecimal.ZERO, totalAmount, null, 1
        );
        ChildOrder childOrder = new ChildOrder(UUID.randomUUID(), vendorId, currency, List.of(item));
        return new ParentOrder(orderId, customerId, status, currency, List.of(childOrder), totalAmount);
    }

    private String buildPaymentAuthorizedPayload(String eventId, String orderId, String amount, String currency, String customerId, Instant occurredAt) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"eventId\":\"").append(eventId).append("\"");
        sb.append(",\"eventType\":\"PaymentAuthorized\"");
        if (orderId != null) sb.append(",\"orderId\":\"").append(orderId).append("\"");
        if (amount != null) sb.append(",\"amount\":\"").append(amount).append("\"");
        if (currency != null) sb.append(",\"currency\":\"").append(currency).append("\"");
        if (customerId != null) sb.append(",\"customerId\":\"").append(customerId).append("\"");
        if (occurredAt != null) sb.append(",\"occurredAt\":\"").append(occurredAt).append("\"");
        sb.append("}");
        return sb.toString();
    }

    @Test
    @DisplayName("P1-02: Matching amount and currency -> ACCEPT and authorize order")
    void matchingAmountAndCurrency_acceptsAndAuthorizesOrder() {
        String eventId = UUID.randomUUID().toString();
        String payload = buildPaymentAuthorizedPayload(
                eventId, testOrder.getId().toString(), "150.00", "USD", CUSTOMER_ID, Instant.now()
        );
        String signature = eventSigner.sign(payload);

        assertDoesNotThrow(() -> consumer.handle(payload, signature));
        assertEquals(OrderStatus.PAYMENT_AUTHORIZED, testOrder.getStatus());
        verify(orderRepository).save(testOrder);
    }

    @Test
    @DisplayName("P1-02: Amount mismatch (underpayment) -> REJECT")
    void amountMismatch_underpayment_isRejected() {
        String eventId = UUID.randomUUID().toString();
        // Attacker pays $10.00 for a $150.00 order
        String payload = buildPaymentAuthorizedPayload(
                eventId, testOrder.getId().toString(), "10.00", "USD", CUSTOMER_ID, Instant.now()
        );
        String signature = eventSigner.sign(payload);

        AmqpRejectAndDontRequeueException ex = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumer.handle(payload, signature)
        );
        assertTrue(ex.getMessage().contains("Payment amount mismatch"));
        assertEquals(OrderStatus.PAYMENT_PENDING, testOrder.getStatus());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("P1-02: Amount mismatch (overpayment/tampered) -> REJECT")
    void amountMismatch_overpayment_isRejected() {
        String eventId = UUID.randomUUID().toString();
        String payload = buildPaymentAuthorizedPayload(
                eventId, testOrder.getId().toString(), "200.00", "USD", CUSTOMER_ID, Instant.now()
        );
        String signature = eventSigner.sign(payload);

        AmqpRejectAndDontRequeueException ex = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumer.handle(payload, signature)
        );
        assertTrue(ex.getMessage().contains("Payment amount mismatch"));
        assertEquals(OrderStatus.PAYMENT_PENDING, testOrder.getStatus());
    }

    @Test
    @DisplayName("P1-02: Currency mismatch (e.g. EUR instead of USD) -> REJECT")
    void currencyMismatch_isRejected() {
        String eventId = UUID.randomUUID().toString();
        String payload = buildPaymentAuthorizedPayload(
                eventId, testOrder.getId().toString(), "150.00", "EUR", CUSTOMER_ID, Instant.now()
        );
        String signature = eventSigner.sign(payload);

        AmqpRejectAndDontRequeueException ex = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumer.handle(payload, signature)
        );
        assertTrue(ex.getMessage().contains("Payment currency mismatch"));
        assertEquals(OrderStatus.PAYMENT_PENDING, testOrder.getStatus());
    }

    @Test
    @DisplayName("P1-02: Missing amount -> REJECT")
    void missingAmount_isRejected() {
        String eventId = UUID.randomUUID().toString();
        String payload = buildPaymentAuthorizedPayload(
                eventId, testOrder.getId().toString(), null, "USD", CUSTOMER_ID, Instant.now()
        );
        String signature = eventSigner.sign(payload);

        AmqpRejectAndDontRequeueException ex = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumer.handle(payload, signature)
        );
        assertTrue(ex.getMessage().contains("financial fields"));
        assertEquals(OrderStatus.PAYMENT_PENDING, testOrder.getStatus());
    }

    @Test
    @DisplayName("P1-02: Missing currency -> REJECT")
    void missingCurrency_isRejected() {
        String eventId = UUID.randomUUID().toString();
        String payload = buildPaymentAuthorizedPayload(
                eventId, testOrder.getId().toString(), "150.00", null, CUSTOMER_ID, Instant.now()
        );
        String signature = eventSigner.sign(payload);

        AmqpRejectAndDontRequeueException ex = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumer.handle(payload, signature)
        );
        assertTrue(ex.getMessage().contains("financial fields"));
        assertEquals(OrderStatus.PAYMENT_PENDING, testOrder.getStatus());
    }

    @Test
    @DisplayName("P1-02: Zero amount -> REJECT")
    void zeroAmount_isRejected() {
        String eventId = UUID.randomUUID().toString();
        String payload = buildPaymentAuthorizedPayload(
                eventId, testOrder.getId().toString(), "0.00", "USD", CUSTOMER_ID, Instant.now()
        );
        String signature = eventSigner.sign(payload);

        AmqpRejectAndDontRequeueException ex = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumer.handle(payload, signature)
        );
        assertTrue(ex.getMessage().contains("strictly positive"));
        assertEquals(OrderStatus.PAYMENT_PENDING, testOrder.getStatus());
    }

    @Test
    @DisplayName("P1-02: Negative amount -> REJECT")
    void negativeAmount_isRejected() {
        String eventId = UUID.randomUUID().toString();
        String payload = buildPaymentAuthorizedPayload(
                eventId, testOrder.getId().toString(), "-50.00", "USD", CUSTOMER_ID, Instant.now()
        );
        String signature = eventSigner.sign(payload);

        AmqpRejectAndDontRequeueException ex = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumer.handle(payload, signature)
        );
        assertTrue(ex.getMessage().contains("strictly positive"));
        assertEquals(OrderStatus.PAYMENT_PENDING, testOrder.getStatus());
    }

    @Test
    @DisplayName("P1-02: Duplicate authorization -> IDEMPOTENT (skipped via idempotency key)")
    void duplicateAuthorization_isIdempotent() {
        String eventId = UUID.randomUUID().toString();
        String payload = buildPaymentAuthorizedPayload(
                eventId, testOrder.getId().toString(), "150.00", "USD", CUSTOMER_ID, Instant.now()
        );
        String signature = eventSigner.sign(payload);

        // First attempt succeeds
        assertDoesNotThrow(() -> consumer.handle(payload, signature));
        assertEquals(OrderStatus.PAYMENT_AUTHORIZED, testOrder.getStatus());

        // Duplicate replay is skipped by idempotency
        when(orderRepository.checkAndSaveIdempotencyKey("payment_event_" + eventId)).thenReturn(false);
        assertDoesNotThrow(() -> consumer.handle(payload, signature));
        assertEquals(OrderStatus.PAYMENT_AUTHORIZED, testOrder.getStatus());
    }

    @Test
    @DisplayName("P1-02: Stale authorization -> REJECTED by freshness check before domain invocation")
    void staleAuthorization_isRejected() {
        String eventId = UUID.randomUUID().toString();
        Instant staleTime = Instant.now().minusSeconds(86400 * 2); // 48 hours ago
        String payload = buildPaymentAuthorizedPayload(
                eventId, testOrder.getId().toString(), "150.00", "USD", CUSTOMER_ID, staleTime
        );
        String signature = eventSigner.sign(payload);

        AmqpRejectAndDontRequeueException ex = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumer.handle(payload, signature)
        );
        assertTrue(ex.getMessage().contains("freshness window") || ex.getMessage().contains("expired"));
        assertEquals(OrderStatus.PAYMENT_PENDING, testOrder.getStatus());
    }

    @Test
    @DisplayName("P1-02: Wrong orderId -> REJECT (Order not found)")
    void wrongOrderId_isRejected() {
        String eventId = UUID.randomUUID().toString();
        UUID nonExistentOrderId = UUID.randomUUID();
        when(orderRepository.findById(nonExistentOrderId)).thenReturn(Optional.empty());

        String payload = buildPaymentAuthorizedPayload(
                eventId, nonExistentOrderId.toString(), "150.00", "USD", CUSTOMER_ID, Instant.now()
        );
        String signature = eventSigner.sign(payload);

        AmqpRejectAndDontRequeueException ex = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumer.handle(payload, signature)
        );
        assertTrue(ex.getMessage().contains("Illegal order state transition") || ex.getMessage().contains("Order not found"));
    }

    @Test
    @DisplayName("P1-02: Invalid state transition (order already CANCELLED) -> REJECT")
    void invalidStateTransition_cancelledOrder_isRejected() {
        ParentOrder cancelledOrder = createTestOrder(OrderStatus.CANCELLED, ORDER_AMOUNT, ORDER_CURRENCY, CUSTOMER_ID);
        when(orderRepository.findById(cancelledOrder.getId())).thenReturn(Optional.of(cancelledOrder));

        String eventId = UUID.randomUUID().toString();
        String payload = buildPaymentAuthorizedPayload(
                eventId, cancelledOrder.getId().toString(), "150.00", "USD", CUSTOMER_ID, Instant.now()
        );
        String signature = eventSigner.sign(payload);

        AmqpRejectAndDontRequeueException ex = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumer.handle(payload, signature)
        );
        assertTrue(ex.getMessage().contains("Cannot mark payment authorized from status: CANCELLED"));
        assertEquals(OrderStatus.CANCELLED, cancelledOrder.getStatus());
    }

    @Test
    @DisplayName("P1-02 ADVERSARIAL: Validly signed PaymentAuthorized event with manipulated amount must fail domain invariant")
    void adversarial_validlySignedEventWithManipulatedAmount_mustFailDomainInvariant() {
        String eventId = UUID.randomUUID().toString();
        // Attacker creates an event with amount 0.01 instead of authoritative 150.00 and signs it with signing key
        String fraudulentPayload = buildPaymentAuthorizedPayload(
                eventId, testOrder.getId().toString(), "0.01", "USD", CUSTOMER_ID, Instant.now()
        );
        String validSignature = eventSigner.sign(fraudulentPayload);

        // Verification must still reject because domain invariant checks authoritative totalAmount!
        AmqpRejectAndDontRequeueException ex = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumer.handle(fraudulentPayload, validSignature)
        );
        assertTrue(ex.getMessage().contains("Payment amount mismatch"));
        assertEquals(OrderStatus.PAYMENT_PENDING, testOrder.getStatus());
    }
}
