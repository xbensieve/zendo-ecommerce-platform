package com.zendo.order.application;

import com.zendo.order.domain.OrderException;
import com.zendo.order.domain.OrderRepository;
import com.zendo.order.domain.ParentOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrderUseCases {

    private final OrderRepository orderRepository;

    public OrderUseCases(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public void markPaymentAuthorized(String orderId) {
        UUID orderUuid = UUID.fromString(orderId);
        ParentOrder order = orderRepository.findById(orderUuid)
                .orElseThrow(() -> new OrderException("Order not found: " + orderId));
        
        order.markPaymentAuthorized();
        orderRepository.save(order);
    }

    @Transactional
    public void markPaymentFailed(String orderId) {
        UUID orderUuid = UUID.fromString(orderId);
        ParentOrder order = orderRepository.findById(orderUuid)
                .orElseThrow(() -> new OrderException("Order not found: " + orderId));
        
        order.markPaymentFailed();
        orderRepository.save(order);
    }
}
