package com.zendo.order.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ChildOrder {
    private final UUID id;
    private final UUID vendorId;
    private final List<OrderItem> items;
    private final String currency;
    private BigDecimal totalAmount;

    public ChildOrder(UUID id, UUID vendorId, String currency, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new OrderException("ChildOrder must contain at least one OrderItem");
        }
        this.id = id;
        this.vendorId = vendorId;
        this.currency = currency;
        this.items = new ArrayList<>(items);
        this.totalAmount = calculateTotal();
    }
    
    // For reconstitution
    public ChildOrder(UUID id, UUID vendorId, String currency, List<OrderItem> items, BigDecimal totalAmount) {
        this.id = id;
        this.vendorId = vendorId;
        this.currency = currency;
        this.items = new ArrayList<>(items);
        this.totalAmount = totalAmount;
    }

    private BigDecimal calculateTotal() {
        return items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public UUID getId() { return id; }
    public UUID getVendorId() { return vendorId; }
    public String getCurrency() { return currency; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
    public BigDecimal getTotalAmount() { return totalAmount; }
}
