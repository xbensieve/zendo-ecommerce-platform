package com.zendo.notification.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zendo.notification.application.NotificationUseCases;
import com.zendo.notification.domain.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Consumes OrderPlaced events from the order module via RabbitMQ.
 * Creates ORDER_CONFIRMED notifications for the customer.
 */
@Component
public class NotificationOrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationOrderEventConsumer.class);
    private final NotificationUseCases notificationUseCases;
    private final ObjectMapper objectMapper;
    private final com.zendo.shared.messaging.EventSigner eventSigner;

    public NotificationOrderEventConsumer(NotificationUseCases notificationUseCases,
                                           ObjectMapper objectMapper,
                                           com.zendo.shared.messaging.EventSigner eventSigner) {
        this.notificationUseCases = notificationUseCases;
        this.objectMapper = objectMapper;
        this.eventSigner = eventSigner;
    }

    @RabbitListener(queues = "notification.order.events.queue")
    public void handle(String message, @org.springframework.messaging.handler.annotation.Header(value = "X-Event-Signature", required = false) String signature) {
        try {
            // 1. Authenticity verification
            if (signature == null || !eventSigner.verify(message, signature)) {
                log.warn("SECURITY ALERT: Untrusted or unsigned RabbitMQ order event detected in Notification context! Discarding message.");
                throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Untrusted or unsigned event signature");
            }

            JsonNode event = objectMapper.readTree(message);
            String eventType = event.get("eventType").asText();

            if ("OrderPlaced".equals(eventType)) {
                if (!event.hasNonNull("eventId") || !event.hasNonNull("orderId") || !event.hasNonNull("totalAmount")) {
                    log.error("Poison message detected in Notification context (Order): missing required fields: {}", com.zendo.shared.messaging.EventSigner.sanitizeForLog(message));
                    throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Missing required event fields");
                }

                // Freshness check: MANDATORY occurredAt for domain events
                if (!event.hasNonNull("occurredAt") || event.get("occurredAt").asText().isBlank()) {
                    log.error("Security violation: missing occurredAt timestamp in Notification context (Order): {}", com.zendo.shared.messaging.EventSigner.sanitizeForLog(message));
                    throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Missing required occurredAt timestamp");
                }

                try {
                    java.time.Instant occurredAt = java.time.Instant.parse(event.get("occurredAt").asText());
                    com.zendo.shared.messaging.EventSigner.validateFreshness(occurredAt, 86400, 300);
                } catch (java.time.format.DateTimeParseException e) {
                    log.error("Malformed occurredAt timestamp in Notification context (Order): {}", com.zendo.shared.messaging.EventSigner.sanitizeForLog(message));
                    throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Invalid occurredAt timestamp", e);
                }

                String eventId = event.get("eventId").asText();
                String customerId = event.get("customerId").asText();
                String orderId = event.get("orderId").asText();
                BigDecimal totalAmount = new BigDecimal(event.get("totalAmount").asText());
                String currency = event.has("currency") ? event.get("currency").asText() : "USD";

                String title = "Order Confirmed";
                String msg = String.format(
                        "Your order %s has been placed successfully. Total: %s %s",
                        orderId, totalAmount.toPlainString(), currency);

                log.info("Processing OrderPlaced for notification, orderId: {}, customerId: {}", orderId, customerId);
                notificationUseCases.createAndSendNotification(
                        customerId, NotificationType.ORDER_CONFIRMED, title, msg, eventId);
            }
        } catch (org.springframework.amqp.AmqpRejectAndDontRequeueException e) {
            throw e;
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            log.error("Poison message detected in Notification context (Order): {}", com.zendo.shared.messaging.EventSigner.sanitizeForLog(message), e);
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Malformed message", e);
        } catch (Exception e) {
            log.error("Failed to process order event in Notification context", e);
            throw new RuntimeException(e);
        }
    }
}
