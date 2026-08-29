package com.zendo.order.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ParentOrderTest {

    @Test
    void shouldCreateParentOrderAndCalculateTotal() {
        UUID vendorId1 = UUID.randomUUID();
        UUID vendorId2 = UUID.randomUUID();

        OrderItem item1 = new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "SKU1", "Product 1", new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("10.00"), null, 2);
        OrderItem item2 = new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "SKU2", "Product 2", new BigDecimal("15.00"), BigDecimal.ZERO, new BigDecimal("15.00"), null, 1);
        
        ChildOrder child1 = new ChildOrder(UUID.randomUUID(), vendorId1, "USD", List.of(item1));
        ChildOrder child2 = new ChildOrder(UUID.randomUUID(), vendorId2, "USD", List.of(item2));

        ParentOrder parent = new ParentOrder(UUID.randomUUID(), "CUST-1", UUID.randomUUID().toString(), "USD", List.of(child1, child2));

        assertEquals(new BigDecimal("35.00"), parent.getTotalAmount());
        assertEquals(2, parent.getChildOrders().size());
        assertEquals(OrderStatus.PAYMENT_PENDING, parent.getStatus());
        
        // Ensure Domain event is registered
        assertEquals(1, parent.getDomainEvents().size());
        assertEquals("OrderPlaced", parent.getDomainEvents().get(0).getClass().getSimpleName());
    }

    @Test
    void shouldThrowExceptionWhenChildOrdersEmpty() {
        assertThrows(OrderException.class, () -> new ParentOrder(UUID.randomUUID(), "cust-1", UUID.randomUUID().toString(), "USD", List.of()));
    }
}
