package com.zendo.notification.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zendo.notification.application.NotificationUseCases;
import com.zendo.notification.domain.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes PaymentAuthorized and PaymentFailed events from the payment module via RabbitMQ.
 * Creates PAYMENT_RECEIVED or PAYMENT_FAILED notifications.
 *
 * Note: PaymentAuthorized/PaymentFailed events now carry customerId from the upstream OrderPlaced event.
 * We use customerId directly as the notification recipient identifier.
 */
@Component
public class NotificationPaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationPaymentEventConsumer.class);
    private final NotificationUseCases notificationUseCases;
    private final ObjectMapper objectMapper;
    private final com.zendo.shared.messaging.EventSigner eventSigner;

    public NotificationPaymentEventConsumer(NotificationUseCases notificationUseCases,
                                             ObjectMapper objectMapper,
                                             com.zendo.shared.messaging.EventSigner eventSigner) {
        this.notificationUseCases = notificationUseCases;
        this.objectMapper = objectMapper;
        this.eventSigner = eventSigner;
    }

    @RabbitListener(queues = "notification.payment.events.queue")
    public void handle(String message, @org.springframework.messaging.handler.annotation.Header(value = "X-Event-Signature", required = false) String signature) {
        try {
            // 1. Authenticity verification
            if (signature == null || !eventSigner.verify(message, signature)) {
                log.warn("SECURITY ALERT: Untrusted or unsigned RabbitMQ payment event detected in Notification context! Discarding message.");
                throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Untrusted or unsigned event signature");
            }

            JsonNode event = objectMapper.readTree(message);
            String eventType = event.get("eventType").asText();
            String eventId = event.get("eventId").asText();

            // Freshness check: MANDATORY occurredAt for domain events
            if (!event.hasNonNull("occurredAt") || event.get("occurredAt").asText().isBlank()) {
                log.error("Security violation: missing occurredAt timestamp in Notification context (Payment): {}", com.zendo.shared.messaging.EventSigner.sanitizeForLog(message));
                throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Missing required occurredAt timestamp");
            }

            try {
                java.time.Instant occurredAt = java.time.Instant.parse(event.get("occurredAt").asText());
                com.zendo.shared.messaging.EventSigner.validateFreshness(occurredAt, 86400, 300);
            } catch (java.time.format.DateTimeParseException e) {
                log.error("Malformed occurredAt timestamp in Notification context (Payment): {}", com.zendo.shared.messaging.EventSigner.sanitizeForLog(message));
                throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Invalid occurredAt timestamp", e);
            }

            if ("PaymentAuthorized".equals(eventType)) {
                String orderId = event.get("orderId").asText();
                String customerId = event.has("customerId") ? event.get("customerId").asText() : "unknown-customer";
                String amount = event.get("amount").asText();
                String currency = event.get("currency").asText();

                String title = "Payment Received";
                String msg = String.format(
                        "Payment of %s %s for order %s has been authorized.",
                        amount, currency, orderId);

                log.info("Processing PaymentAuthorized for notification, orderId: {}, customerId: {}", orderId, customerId);
                notificationUseCases.createAndSendNotification(
                        customerId, NotificationType.PAYMENT_RECEIVED, title, msg, eventId);

            } else if ("PaymentFailed".equals(eventType)) {
                String orderId = event.get("orderId").asText();
                String customerId = event.has("customerId") ? event.get("customerId").asText() : "unknown-customer";
                String reason = event.has("reason") ? event.get("reason").asText() : "Unknown reason";

                String title = "Payment Failed";
                String msg = String.format(
                        "Payment for order %s has failed. Reason: %s",
                        orderId, reason);

                log.info("Processing PaymentFailed for notification, orderId: {}, customerId: {}", orderId, customerId);
                notificationUseCases.createAndSendNotification(
                        customerId, NotificationType.PAYMENT_FAILED, title, msg, eventId);
            }
        } catch (org.springframework.amqp.AmqpRejectAndDontRequeueException e) {
            throw e;
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            log.error("Poison message detected in Notification context (Payment): {}", com.zendo.shared.messaging.EventSigner.sanitizeForLog(message), e);
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Malformed message", e);
        } catch (Exception e) {
            log.error("Failed to process payment event in Notification context", e);
            throw new RuntimeException(e);
        }
    }
}
