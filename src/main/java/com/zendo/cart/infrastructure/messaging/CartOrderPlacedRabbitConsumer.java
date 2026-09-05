package com.zendo.cart.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zendo.cart.application.CartUseCases;
import com.zendo.shared.messaging.IdempotentEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CartOrderPlacedRabbitConsumer {

    private static final Logger log = LoggerFactory.getLogger(CartOrderPlacedRabbitConsumer.class);
    private final CartUseCases cartUseCases;
    private final ObjectMapper objectMapper;
    private final com.zendo.shared.messaging.EventSigner eventSigner;

    public CartOrderPlacedRabbitConsumer(CartUseCases cartUseCases, ObjectMapper objectMapper, com.zendo.shared.messaging.EventSigner eventSigner) {
        this.cartUseCases = cartUseCases;
        this.objectMapper = objectMapper;
        this.eventSigner = eventSigner;
    }

    @RabbitListener(queues = "cart.order.events.queue")
    @Transactional
    public void handle(String message, @org.springframework.messaging.handler.annotation.Header(value = "X-Event-Signature", required = false) String signature) {
        try {
            // 1. Authenticity verification
            if (signature == null || !eventSigner.verify(message, signature)) {
                log.warn("SECURITY ALERT: Untrusted or unsigned RabbitMQ OrderPlaced event detected in Cart context! Discarding message.");
                throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Untrusted or unsigned event signature");
            }

            JsonNode event = objectMapper.readTree(message);
            String eventType = event.get("eventType").asText();
            
            if ("OrderPlaced".equals(eventType)) {
                if (!event.hasNonNull("eventId") || !event.hasNonNull("cartId")) {
                    log.error("Poison message detected in Cart context: missing eventId or cartId: {}", com.zendo.shared.messaging.EventSigner.sanitizeForLog(message));
                    throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Missing required event fields");
                }

                // Freshness check: MANDATORY occurredAt for security-sensitive domain events
                if (!event.hasNonNull("occurredAt") || event.get("occurredAt").asText().isBlank()) {
                    log.error("Security violation: missing occurredAt timestamp in Cart context: {}", com.zendo.shared.messaging.EventSigner.sanitizeForLog(message));
                    throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Missing required occurredAt timestamp");
                }

                try {
                    java.time.Instant occurredAt = java.time.Instant.parse(event.get("occurredAt").asText());
                    com.zendo.shared.messaging.EventSigner.validateFreshness(occurredAt, 86400, 300);
                } catch (java.time.format.DateTimeParseException e) {
                    log.error("Malformed occurredAt timestamp in Cart context: {}", com.zendo.shared.messaging.EventSigner.sanitizeForLog(message));
                    throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Invalid occurredAt timestamp", e);
                }

                String eventId = event.get("eventId").asText();
                String cartId = event.get("cartId").asText();
                
                log.info("Processing OrderPlaced for cart clearing, eventId: {}", eventId);
                if (!"FLASH_SALE".equals(cartId)) {
                    cartUseCases.clearCartById(cartId);
                }
            }
        } catch (org.springframework.amqp.AmqpRejectAndDontRequeueException e) {
            throw e;
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            log.error("Poison message detected in Cart context: {}", com.zendo.shared.messaging.EventSigner.sanitizeForLog(message), e);
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Malformed message", e);
        } catch (Exception e) {
            log.error("Failed to process message in Cart context", e);
            throw new RuntimeException(e); // Let RabbitMQ retry or DLQ
        }
    }
}
