package com.zendo.order.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zendo.order.application.OrderUseCases;
import com.zendo.shared.messaging.IdempotentEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentResultRabbitConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultRabbitConsumer.class);
    private final OrderUseCases orderUseCases;
    private final ObjectMapper objectMapper;

    public PaymentResultRabbitConsumer(OrderUseCases orderUseCases, ObjectMapper objectMapper) {
        this.orderUseCases = orderUseCases;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "order.payment.events.queue")
    @Transactional
    public void handle(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventType = event.get("eventType").asText();
            String eventId = event.get("eventId").asText();
            
            if ("PaymentAuthorized".equals(eventType)) {
                String orderId = event.get("orderId").asText();
                log.info("Processing PaymentAuthorized for order: {}", orderId);
                orderUseCases.markPaymentAuthorized(orderId);
            } else if ("PaymentFailed".equals(eventType)) {
                String orderId = event.get("orderId").asText();
                log.info("Processing PaymentFailed for order: {}", orderId);
                orderUseCases.markPaymentFailed(orderId);
            }
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            log.warn("Order not found for payment event. Discarding message.", e);
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Order not found", e);
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            log.error("Poison message detected in Order context: {}", message, e);
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Malformed message", e);
        } catch (Exception e) {
            log.error("Failed to process message in Order context", e);
            throw new RuntimeException(e);
        }
    }
}
