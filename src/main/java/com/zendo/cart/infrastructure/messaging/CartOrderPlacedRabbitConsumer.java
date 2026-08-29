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

    public CartOrderPlacedRabbitConsumer(CartUseCases cartUseCases, ObjectMapper objectMapper) {
        this.cartUseCases = cartUseCases;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "cart.order.events.queue")
    @Transactional
    public void handle(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventType = event.get("eventType").asText();
            
            if ("OrderPlaced".equals(eventType)) {
                String eventId = event.get("eventId").asText();
                String cartId = event.get("cartId").asText();
                
                log.info("Processing OrderPlaced for cart clearing, eventId: {}", eventId);
                if (!"FLASH_SALE".equals(cartId)) {
                    cartUseCases.clearCartById(cartId);
                }
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            log.error("Poison message detected in Cart context: {}", message, e);
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Malformed message", e);
        } catch (Exception e) {
            log.error("Failed to process message in Cart context", e);
            throw new RuntimeException(e); // Let RabbitMQ retry or DLQ
        }
    }
}
