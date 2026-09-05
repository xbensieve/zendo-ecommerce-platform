package com.zendo.order.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zendo.order.application.OrderUseCases;
import com.zendo.order.domain.OrderRepository;
import com.zendo.shared.messaging.EventSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentResultRabbitConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultRabbitConsumer.class);
    private final OrderUseCases orderUseCases;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    private final EventSigner eventSigner;

    public PaymentResultRabbitConsumer(
            OrderUseCases orderUseCases, 
            OrderRepository orderRepository, 
            ObjectMapper objectMapper,
            EventSigner eventSigner) {
        this.orderUseCases = orderUseCases;
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
        this.eventSigner = eventSigner;
    }

    @RabbitListener(queues = "order.payment.events.queue")
    @Transactional
    public void handle(String message, @Header(value = "X-Event-Signature", required = false) String signature) {
        try {
            // 1. Authenticity verification
            if (signature == null || !eventSigner.verify(message, signature)) {
                log.warn("SECURITY ALERT: Untrusted or unsigned RabbitMQ payment event detected! Discarding message.");
                throw new AmqpRejectAndDontRequeueException("Untrusted or unsigned event signature");
            }

            // 2. Parse and validate JSON schema
            JsonNode event = objectMapper.readTree(message);
            if (!event.hasNonNull("eventType") || !event.hasNonNull("eventId") || !event.hasNonNull("orderId")) {
                log.error("Poison message detected in Order context: missing required fields: {}", EventSigner.sanitizeForLog(message));
                throw new AmqpRejectAndDontRequeueException("Missing required event fields");
            }

            // Freshness check: MANDATORY occurredAt for security-sensitive domain events
            if (!event.hasNonNull("occurredAt") || event.get("occurredAt").asText().isBlank()) {
                log.error("Security violation: missing occurredAt timestamp in payment event: {}", EventSigner.sanitizeForLog(message));
                throw new AmqpRejectAndDontRequeueException("Missing required occurredAt timestamp");
            }

            try {
                java.time.Instant occurredAt = java.time.Instant.parse(event.get("occurredAt").asText());
                EventSigner.validateFreshness(occurredAt, 86400, 300);
            } catch (java.time.format.DateTimeParseException e) {
                log.error("Malformed occurredAt timestamp in payment event: {}", EventSigner.sanitizeForLog(message));
                throw new AmqpRejectAndDontRequeueException("Invalid occurredAt timestamp", e);
            }

            String eventType = event.get("eventType").asText();
            String eventId = event.get("eventId").asText();

            // 3. Idempotency Check
            if (!orderRepository.checkAndSaveIdempotencyKey("payment_event_" + eventId)) {
                log.info("Duplicate payment event {} detected in Order context, skipping.", eventId);
                return;
            }
            
            // 4. State transition
            if ("PaymentAuthorized".equals(eventType)) {
                if (!event.hasNonNull("amount") || !event.hasNonNull("currency")) {
                    log.error("Security violation: missing financial fields in PaymentAuthorized: {}", EventSigner.sanitizeForLog(message));
                    throw new AmqpRejectAndDontRequeueException("Missing required financial fields (amount, currency)");
                }

                java.math.BigDecimal amount;
                try {
                    amount = new java.math.BigDecimal(event.get("amount").asText());
                } catch (Exception e) {
                    log.error("Malformed amount in PaymentAuthorized: {}", EventSigner.sanitizeForLog(message));
                    throw new AmqpRejectAndDontRequeueException("Malformed amount field", e);
                }

                if (amount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                    log.error("Security alert: non-positive amount in PaymentAuthorized: {}", amount);
                    throw new AmqpRejectAndDontRequeueException("Amount must be strictly positive");
                }

                String currency = event.get("currency").asText();
                if (currency.isBlank()) {
                    throw new AmqpRejectAndDontRequeueException("Currency cannot be blank");
                }

                String orderId = event.get("orderId").asText();
                String customerId = event.hasNonNull("customerId") ? event.get("customerId").asText() : null;

                log.info("Processing PaymentAuthorized for order: {}, amount: {}, currency: {}", orderId, amount, currency);
                orderUseCases.markPaymentAuthorized(orderId, amount, currency, customerId);
            } else if ("PaymentFailed".equals(eventType)) {
                String orderId = event.get("orderId").asText();
                log.info("Processing PaymentFailed for order: {}", orderId);
                orderUseCases.markPaymentFailed(orderId);
            }
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            log.warn("Order not found for payment event. Discarding message.", e);
            throw new AmqpRejectAndDontRequeueException("Order not found", e);
        } catch (com.zendo.order.domain.OrderException e) {
            log.warn("Illegal order state transition for payment event: {}", e.getMessage());
            throw new AmqpRejectAndDontRequeueException("Illegal order state transition: " + e.getMessage(), e);
        } catch (AmqpRejectAndDontRequeueException e) {
            throw e;
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            log.error("Poison message detected in Order context: {}", EventSigner.sanitizeForLog(message), e);
            throw new AmqpRejectAndDontRequeueException("Malformed message", e);
        } catch (Exception e) {
            log.error("Failed to process message in Order context", e);
            throw new RuntimeException(e);
        }
    }
}
