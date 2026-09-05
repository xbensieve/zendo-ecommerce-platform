package com.zendo.payment.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zendo.payment.application.PaymentUseCases;
import com.zendo.shared.messaging.IdempotentEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
public class OrderPlacedRabbitConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderPlacedRabbitConsumer.class);
    private final PaymentUseCases paymentUseCases;
    private final ObjectMapper objectMapper;
    private final com.zendo.shared.messaging.EventSigner eventSigner;

    public OrderPlacedRabbitConsumer(PaymentUseCases paymentUseCases, ObjectMapper objectMapper, com.zendo.shared.messaging.EventSigner eventSigner) {
        this.paymentUseCases = paymentUseCases;
        this.objectMapper = objectMapper;
        this.eventSigner = eventSigner;
    }

    @RabbitListener(queues = "payment.order.events.queue")
    @Transactional
    public void handle(String message, @org.springframework.messaging.handler.annotation.Header(value = "X-Event-Signature", required = false) String signature) {
        try {
            // 1. Authenticity verification
            if (signature == null || !eventSigner.verify(message, signature)) {
                log.warn("SECURITY ALERT: Untrusted or unsigned RabbitMQ OrderPlaced event detected! Discarding message.");
                throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Untrusted or unsigned event signature");
            }

            JsonNode event = objectMapper.readTree(message);
            String eventType = event.get("eventType").asText();
            
            if ("OrderPlaced".equals(eventType)) {
                if (!event.hasNonNull("eventId") || !event.hasNonNull("orderId") || !event.hasNonNull("totalAmount")) {
                    log.error("Poison message detected in Payment context: missing required fields: {}", com.zendo.shared.messaging.EventSigner.sanitizeForLog(message));
                    throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Missing required event fields");
                }

                // Freshness check: MANDATORY occurredAt for security-sensitive domain events
                if (!event.hasNonNull("occurredAt") || event.get("occurredAt").asText().isBlank()) {
                    log.error("Security violation: missing occurredAt timestamp in OrderPlaced event: {}", com.zendo.shared.messaging.EventSigner.sanitizeForLog(message));
                    throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Missing required occurredAt timestamp");
                }

                try {
                    java.time.Instant occurredAt = java.time.Instant.parse(event.get("occurredAt").asText());
                    com.zendo.shared.messaging.EventSigner.validateFreshness(occurredAt, 86400, 300);
                } catch (java.time.format.DateTimeParseException e) {
                    log.error("Malformed occurredAt timestamp in OrderPlaced event: {}", com.zendo.shared.messaging.EventSigner.sanitizeForLog(message));
                    throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Invalid occurredAt timestamp", e);
                }

                String eventId = event.get("eventId").asText();
                String orderId = event.get("orderId").asText();
                String customerId = event.has("customerId") ? event.get("customerId").asText() : "unknown-customer";
                BigDecimal amount = new BigDecimal(event.get("totalAmount").asText());
                String currency = event.has("currency") ? event.get("currency").asText() : "USD";
                log.info("Processing OrderPlaced for payment authorization, orderId: {}", orderId);
                // Use eventId as idempotency key for the payment authorization
                paymentUseCases.authorizePayment(orderId, customerId, amount, currency, "auth_" + eventId);
            }
        } catch (org.springframework.amqp.AmqpRejectAndDontRequeueException e) {
            throw e;
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            log.error("Poison message detected in Payment context (malformed): {}", com.zendo.shared.messaging.EventSigner.sanitizeForLog(message), e);
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Malformed message", e);
        } catch (Exception e) {
            log.error("Failed to process message in Payment context", e);
            throw new RuntimeException(e);
        }
    }
}
