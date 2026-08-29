package com.zendo.order.api;

import com.zendo.order.domain.ChildOrder;
import com.zendo.order.domain.OrderItem;
import com.zendo.order.domain.OrderStatus;
import com.zendo.order.domain.ParentOrder;
import com.zendo.order.domain.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrderQueryApiImpl implements OrderQueryApi {

    private final OrderRepository orderRepository;

    public OrderQueryApiImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEligibleForReview(String customerId, UUID orderItemId) {
        // We need to find if there is a ParentOrder for this customerId that contains this orderItemId,
        // and that order must be PAYMENT_AUTHORIZED.
        // Since OrderRepository only provides findById and save, we might need a custom query or 
        // iterate. Wait, OrderRepository only provides `findById(UUID id)`. 
        // If we don't have a specific find method in the repository, we can either:
        // 1. Add it to the OrderRepository.
        // 2. Use a direct JPA/JDBC query here.
        // The most DDD-compliant way is adding `boolean hasPaidOrderItem(String customerId, UUID orderItemId)` to OrderRepository.
        return orderRepository.hasPaidOrderItem(customerId, orderItemId);
    }
}
