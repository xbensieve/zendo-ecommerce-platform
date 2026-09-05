package com.zendo.order.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zendo.order.domain.ChildOrder;
import com.zendo.order.domain.OrderItem;
import com.zendo.order.domain.OrderRepository;
import com.zendo.order.domain.OrderStatus;
import com.zendo.order.domain.OrderException;
import com.zendo.order.domain.ParentOrder;
import com.zendo.shared.messaging.DomainEvent;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class JdbcOrderRepository implements OrderRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcOrderRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean checkAndSaveIdempotencyKey(String idempotencyKey) {
        return checkAndSaveIdempotencyKey(idempotencyKey, null);
    }

    @Override
    public boolean checkAndSaveIdempotencyKey(String idempotencyKey, String payloadHash) {
        var existing = jdbcClient.sql("SELECT payload_hash FROM order_ctx.idempotency_keys WHERE key_value = :key")
                .param("key", idempotencyKey)
                .query().listOfRows();
        if (!existing.isEmpty()) {
            if (payloadHash != null) {
                String existingHash = (String) existing.get(0).get("payload_hash");
                if (existingHash != null && !existingHash.equals(payloadHash)) {
                    throw new OrderException("Idempotency key payload mismatch for key: " + idempotencyKey);
                }
            }
            return false;
        }

        try {
            jdbcClient.sql("INSERT INTO order_ctx.idempotency_keys (key_value, payload_hash) VALUES (:key, :payloadHash)")
                    .param("key", idempotencyKey)
                    .param("payloadHash", payloadHash)
                    .update();
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    public void save(ParentOrder order) {
        jdbcClient.sql("INSERT INTO order_ctx.parent_orders (id, customer_id, status, total_amount, currency) VALUES (:id, :customerId, :status, :totalAmount, :currency) " +
                       "ON CONFLICT (id) DO UPDATE SET status = :status, updated_at = NOW()")
                .param("id", order.getId())
                .param("customerId", order.getCustomerId())
                .param("status", order.getStatus().name())
                .param("totalAmount", order.getTotalAmount())
                .param("currency", order.getCurrency())
                .update();

        // Delete existing children and items for simplicity of update (or we could only insert, assuming orders are mostly append-only/immutable structure)
        jdbcClient.sql("DELETE FROM order_ctx.child_orders WHERE parent_order_id = :parentId")
                .param("parentId", order.getId())
                .update();

        for (ChildOrder childOrder : order.getChildOrders()) {
            jdbcClient.sql("INSERT INTO order_ctx.child_orders (id, parent_order_id, vendor_id, total_amount, currency) VALUES (:id, :parentId, :vendorId, :totalAmount, :currency)")
                    .param("id", childOrder.getId())
                    .param("parentId", order.getId())
                    .param("vendorId", childOrder.getVendorId())
                    .param("totalAmount", childOrder.getTotalAmount())
                    .param("currency", childOrder.getCurrency())
                    .update();

            for (OrderItem item : childOrder.getItems()) {
                jdbcClient.sql("INSERT INTO order_ctx.order_items (id, child_order_id, product_id, sku, product_name, original_unit_price, applied_discount, final_unit_price, promotion_ref, quantity, line_total) " +
                               "VALUES (:id, :childOrderId, :productId, :sku, :productName, :originalUnitPrice, :appliedDiscount, :finalUnitPrice, :promotionRef, :quantity, :lineTotal)")
                        .param("id", item.getId())
                        .param("childOrderId", childOrder.getId())
                        .param("productId", item.getProductId())
                        .param("sku", item.getSku())
                        .param("productName", item.getProductName())
                        .param("originalUnitPrice", item.getOriginalUnitPrice())
                        .param("appliedDiscount", item.getAppliedDiscount())
                        .param("finalUnitPrice", item.getFinalUnitPrice())
                        .param("promotionRef", item.getPromotionRef())
                        .param("quantity", item.getQuantity())
                        .param("lineTotal", item.getLineTotal())
                        .update();
            }
        }
        
        // Save domain events as outbox messages
        for (DomainEvent event : order.getDomainEvents()) {
            try {
                String payload = objectMapper.writeValueAsString(event);
                jdbcClient.sql("INSERT INTO order_ctx.outbox_events (id, aggregate_type, aggregate_id, event_type, payload) VALUES (:id, :aggregateType, :aggregateId, :type, :payload::jsonb)")
                        .param("id", event.getEventId())
                        .param("aggregateType", "ParentOrder")
                        .param("aggregateId", order.getId().toString())
                        .param("type", event.getClass().getSimpleName())
                        .param("payload", payload)
                        .update();
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize domain event", e);
            }
        }
        order.clearDomainEvents();
    }

    @Override
    public Optional<ParentOrder> findById(UUID id) {
        var parentRow = jdbcClient.sql("SELECT * FROM order_ctx.parent_orders WHERE id = :id")
                .param("id", id)
                .query().singleRow();

        if (parentRow == null || parentRow.isEmpty()) {
            return Optional.empty();
        }

        var childRows = jdbcClient.sql("SELECT * FROM order_ctx.child_orders WHERE parent_order_id = :parentId")
                .param("parentId", id)
                .query().listOfRows();
        
        List<ChildOrder> childOrders = new ArrayList<>();
        for (var crow : childRows) {
            UUID childId = (UUID) crow.get("id");
            var itemRows = jdbcClient.sql("SELECT * FROM order_ctx.order_items WHERE child_order_id = :childOrderId")
                    .param("childOrderId", childId)
                    .query().listOfRows();
            
            List<OrderItem> items = itemRows.stream().map(irow -> new OrderItem(
                    (UUID) irow.get("id"),
                    (UUID) irow.get("product_id"),
                    (String) irow.get("sku"),
                    (String) irow.get("product_name"),
                    (BigDecimal) irow.get("original_unit_price"),
                    (BigDecimal) irow.get("applied_discount"),
                    (BigDecimal) irow.get("final_unit_price"),
                    (String) irow.get("promotion_ref"),
                    (Integer) irow.get("quantity"),
                    (BigDecimal) irow.get("line_total")
            )).collect(Collectors.toList());
            
            childOrders.add(new ChildOrder(
                    childId,
                    (UUID) crow.get("vendor_id"),
                    (String) crow.get("currency"),
                    items,
                    (BigDecimal) crow.get("total_amount")
            ));
        }

        return Optional.of(new ParentOrder(
                (UUID) parentRow.get("id"),
                (String) parentRow.get("customer_id"),
                OrderStatus.valueOf((String) parentRow.get("status")),
                (String) parentRow.get("currency"),
                childOrders,
                (BigDecimal) parentRow.get("total_amount")
        ));
    }

    @Override
    public boolean hasPaidOrderItem(String customerId, UUID orderItemId, UUID productId) {
        String sql = """
            SELECT COUNT(1)
            FROM order_ctx.parent_orders p
            JOIN order_ctx.child_orders c ON p.id = c.parent_order_id
            JOIN order_ctx.order_items i ON c.id = i.child_order_id
            WHERE p.customer_id = :customerId
              AND i.id = :orderItemId
              AND i.product_id = :productId
              AND p.status = 'PAYMENT_AUTHORIZED'
        """;
        Integer count = jdbcClient.sql(sql)
                .param("customerId", customerId)
                .param("orderItemId", orderItemId)
                .param("productId", productId)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }
}
