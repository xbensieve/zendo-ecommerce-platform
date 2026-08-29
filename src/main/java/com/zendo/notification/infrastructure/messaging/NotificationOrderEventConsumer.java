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

    public NotificationOrderEventConsumer(NotificationUseCases notificationUseCases,
                                           ObjectMapper objectMapper) {
        this.notificationUseCases = notificationUseCases;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "notification.order.events.queue")
    public void handle(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventType = event.get("eventType").asText();

            if ("OrderPlaced".equals(eventType)) {
                String eventId = event.get("eventId").asText();
                String customerId = event.get("customerId").asText();
                String orderId = event.get("orderId").asText();
                BigDecimal totalAmount = new BigDecimal(event.get("totalAmount").asText());
                String currency = event.get("currency").asText();

                String title = "Order Confirmed";
                String msg = String.format(
                        "Your order %s has been placed successfully. Total: %s %s",
                        orderId, totalAmount.toPlainString(), currency);

                log.info("Processing OrderPlaced for notification, orderId: {}, customerId: {}", orderId, customerId);
                notificationUseCases.createAndSendNotification(
                        customerId, NotificationType.ORDER_CONFIRMED, title, msg, eventId);
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            log.error("Poison message detected in Notification context (Order): {}", message, e);
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Malformed message", e);
        } catch (Exception e) {
            log.error("Failed to process order event in Notification context", e);
            throw new RuntimeException(e);
        }
    }
}
