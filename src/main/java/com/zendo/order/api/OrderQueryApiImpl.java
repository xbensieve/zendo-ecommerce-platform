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
    public boolean isEligibleForReview(String customerId, UUID orderItemId, UUID productId) {
        return orderRepository.hasPaidOrderItem(customerId, orderItemId, productId);
    }
}
