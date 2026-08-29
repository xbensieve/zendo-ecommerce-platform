package com.zendo.payment.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zendo.payment.domain.PaymentRepository;
import com.zendo.payment.domain.PaymentStatus;
import com.zendo.payment.domain.PaymentTransaction;
import com.zendo.shared.messaging.DomainEvent;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPaymentRepository implements PaymentRepository {
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcPaymentRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(PaymentTransaction payment) {
        boolean exists = jdbcClient.sql("SELECT count(*) FROM payment.payment_transactions WHERE id = :id")
                .param("id", payment.getId())
                .query(Integer.class)
                .single() > 0;

        if (exists) {
            jdbcClient.sql("UPDATE payment.payment_transactions SET status = :status, gateway_reference = :gatewayReference, updated_at = NOW() WHERE id = :id")
                    .param("id", payment.getId())
                    .param("status", payment.getStatus().name())
                    .param("gatewayReference", payment.getGatewayReference())
                    .update();
        } else {
            jdbcClient.sql("INSERT INTO payment.payment_transactions (id, order_id, customer_id, amount, currency, status, gateway_reference) VALUES (:id, :orderId, :customerId, :amount, :currency, :status, :gatewayReference)")
                    .param("id", payment.getId())
                    .param("orderId", payment.getOrderId())
                    .param("customerId", payment.getCustomerId())
                    .param("amount", payment.getAmount())
                    .param("currency", payment.getCurrency())
                    .param("status", payment.getStatus().name())
                    .param("gatewayReference", payment.getGatewayReference())
                    .update();
        }

        for (DomainEvent event : payment.getDomainEvents()) {
            try {
                String payload = objectMapper.writeValueAsString(event);
                jdbcClient.sql("INSERT INTO payment.outbox_events (id, aggregate_type, aggregate_id, event_type, payload) VALUES (:id, :aggregateType, :aggregateId, :type, :payload::jsonb)")
                        .param("id", event.getEventId())
                        .param("aggregateType", "PaymentTransaction")
                        .param("aggregateId", payment.getId().toString())
                        .param("type", event.getEventType())
                        .param("payload", payload)
                        .update();
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize domain event", e);
            }
        }
        payment.clearDomainEvents();
    }

    @Override
    public Optional<PaymentTransaction> findById(UUID id) {
        return jdbcClient.sql("SELECT id, order_id, customer_id, amount, currency, status, gateway_reference FROM payment.payment_transactions WHERE id = :id")
                .param("id", id)
                .query((rs, rowNum) -> new PaymentTransaction(
                        rs.getObject("id", UUID.class),
                        rs.getObject("order_id", UUID.class),
                        rs.getString("customer_id"),
                        rs.getBigDecimal("amount"),
                        rs.getString("currency"),
                        rs.getString("gateway_reference"),
                        PaymentStatus.valueOf(rs.getString("status"))
                ))
                .optional();
    }

    @Override
    public Optional<PaymentTransaction> findByOrderId(UUID orderId) {
        return jdbcClient.sql("SELECT id, order_id, customer_id, amount, currency, status, gateway_reference FROM payment.payment_transactions WHERE order_id = :orderId")
                .param("orderId", orderId)
                .query((rs, rowNum) -> new PaymentTransaction(
                        rs.getObject("id", UUID.class),
                        rs.getObject("order_id", UUID.class),
                        rs.getString("customer_id"),
                        rs.getBigDecimal("amount"),
                        rs.getString("currency"),
                        rs.getString("gateway_reference"),
                        PaymentStatus.valueOf(rs.getString("status"))
                ))
                .optional();
    }

    @Override
    public boolean checkAndSaveIdempotencyKey(String key) {
        try {
            jdbcClient.sql("INSERT INTO payment.idempotency_keys (key_value) VALUES (:key)")
                    .param("key", key)
                    .update();
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }
}
