package com.zendo.order.domain;

import com.zendo.shared.messaging.DomainEvent;
import com.zendo.order.domain.events.OrderPlaced;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ParentOrder {
    private final UUID id;
    private final String customerId;
    private OrderStatus status;
    private final List<ChildOrder> childOrders;
    private final String currency;
    private BigDecimal totalAmount;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    public ParentOrder(UUID id, String customerId, String cartId, String currency, List<ChildOrder> childOrders) {
        if (childOrders == null || childOrders.isEmpty()) {
            throw new OrderException("ParentOrder must contain at least one ChildOrder");
        }
        this.id = id;
        this.customerId = customerId;
        this.status = OrderStatus.PAYMENT_PENDING;
        this.currency = currency;
        this.childOrders = new ArrayList<>(childOrders);
        this.totalAmount = calculateTotal();
        
        verifyInvariants();
        
        registerEvent(new OrderPlaced(
                UUID.randomUUID(),
                Instant.now(),
                id.toString(),
                customerId,
                cartId,
                totalAmount,
                currency
        ));
    }
    
    // For reconstitution
    public ParentOrder(UUID id, String customerId, OrderStatus status, String currency, List<ChildOrder> childOrders, BigDecimal totalAmount) {
        this.id = id;
        this.customerId = customerId;
        this.status = status;
        this.currency = currency;
        this.childOrders = new ArrayList<>(childOrders);
        this.totalAmount = totalAmount;
        
        verifyInvariants();
    }
    
    private BigDecimal calculateTotal() {
        return childOrders.stream()
                .map(ChildOrder::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private void verifyInvariants() {
        BigDecimal calculatedTotal = calculateTotal();
        if (calculatedTotal.compareTo(totalAmount) != 0) {
            throw new OrderException("ParentOrder total does not match sum of ChildOrder totals");
        }
    }

    public void markPaymentAuthorized(BigDecimal amount, String currency, String customerId) {
        if (amount == null) {
            throw new OrderException("Payment amount is required");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new OrderException("Payment amount must be strictly positive");
        }
        if (currency == null || currency.trim().isEmpty()) {
            throw new OrderException("Payment currency is required");
        }
        if (amount.compareTo(this.totalAmount) != 0) {
            throw new OrderException(String.format("Payment amount mismatch: expected %s but received %s", this.totalAmount, amount));
        }
        if (!currency.trim().equalsIgnoreCase(this.currency.trim())) {
            throw new OrderException(String.format("Payment currency mismatch: expected %s but received %s", this.currency, currency));
        }
        if (customerId != null && !customerId.isBlank() && !customerId.trim().equals(this.customerId.trim())) {
            throw new OrderException(String.format("Payment customerId mismatch: expected %s but received %s", this.customerId, customerId));
        }

        if (this.status == OrderStatus.PAYMENT_AUTHORIZED) {
            return; // Idempotent
        }
        if (this.status != OrderStatus.PAYMENT_PENDING && this.status != OrderStatus.PAYMENT_FAILED) {
            throw new OrderException("Cannot mark payment authorized from status: " + this.status);
        }
        this.status = OrderStatus.PAYMENT_AUTHORIZED;
    }

    public void markPaymentAuthorized(BigDecimal amount, String currency) {
        markPaymentAuthorized(amount, currency, null);
    }

    public void markPaymentAuthorized() {
        markPaymentAuthorized(this.totalAmount, this.currency, null);
    }

    public void markPaymentFailed() {
        if (this.status == OrderStatus.PAYMENT_FAILED) {
            return; // Idempotent
        }
        if (this.status != OrderStatus.PAYMENT_PENDING) {
            throw new OrderException("Cannot mark payment failed from status: " + this.status);
        }
        this.status = OrderStatus.PAYMENT_FAILED;
    }

    public void cancel() {
        if (this.status == OrderStatus.CANCELLED) {
            return; // Idempotent
        }
        if (this.status != OrderStatus.PAYMENT_PENDING && this.status != OrderStatus.PAYMENT_FAILED) {
            throw new OrderException("Cannot cancel order from status: " + this.status);
        }
        this.status = OrderStatus.CANCELLED;
    }

    public UUID getId() { return id; }
    public String getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public String getCurrency() { return currency; }
    public List<ChildOrder> getChildOrders() { return Collections.unmodifiableList(childOrders); }
    public BigDecimal getTotalAmount() { return totalAmount; }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        this.domainEvents.clear();
    }

    private void registerEvent(DomainEvent event) {
        this.domainEvents.add(event);
    }
}
