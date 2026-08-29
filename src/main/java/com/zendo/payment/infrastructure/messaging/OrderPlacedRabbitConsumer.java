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

    public OrderPlacedRabbitConsumer(PaymentUseCases paymentUseCases, ObjectMapper objectMapper) {
        this.paymentUseCases = paymentUseCases;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "payment.order.events.queue")
    @Transactional
    public void handle(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventType = event.get("eventType").asText();
            
            if ("OrderPlaced".equals(eventType)) {
                String eventId = event.get("eventId").asText();
                String orderId = event.get("orderId").asText();
                String customerId = event.has("customerId") ? event.get("customerId").asText() : "unknown-customer";
                BigDecimal amount = new BigDecimal(event.get("totalAmount").asText());
                String currency = event.get("currency").asText();
                log.info("Processing OrderPlaced for payment authorization, orderId: {}", orderId);
                // Use eventId as idempotency key for the payment authorization
                paymentUseCases.authorizePayment(orderId, customerId, amount, currency, "auth_" + eventId);
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            log.error("Poison message detected in Payment context (malformed): {}", message, e);
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Malformed message", e);
        } catch (Exception e) {
            log.error("Failed to process message in Payment context", e);
            throw new RuntimeException(e);
        }
    }
}
