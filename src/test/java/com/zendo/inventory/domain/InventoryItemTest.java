package com.zendo.inventory.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InventoryItemTest {

    @Test
    void initialize_CreatesItemWithZeroStock() {
        UUID productId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();

        InventoryItem item = InventoryItem.initialize(productId, vendorId);

        assertEquals(0, item.getOnHand().value());
        assertEquals(0, item.getReserved().value());
        assertEquals(0, item.getAvailable().value());
    }

    @Test
    void adjustStock_IncreasesOnHandAndAvailable() {
        InventoryItem item = InventoryItem.initialize(UUID.randomUUID(), UUID.randomUUID());

        item.adjustStock(new Quantity(10), "TEST-REF");

        assertEquals(10, item.getOnHand().value());
        assertEquals(0, item.getReserved().value());
        assertEquals(10, item.getAvailable().value());
    }

    @Test
    void adjustStock_CannotAdjustBelowReserved() {
        InventoryItem item = InventoryItem.reconstitute(UUID.randomUUID(), UUID.randomUUID(), 10, 5);

        assertThrows(InventoryException.class, () -> item.adjustStock(new Quantity(4), "TEST-REF"));
    }

    @Test
    void reserve_DecreasesAvailableAndIncreasesReserved() {
        InventoryItem item = InventoryItem.reconstitute(UUID.randomUUID(), UUID.randomUUID(), 10, 0);

        item.reserve(new Quantity(4), "TEST-REF");

        assertEquals(10, item.getOnHand().value());
        assertEquals(4, item.getReserved().value());
        assertEquals(6, item.getAvailable().value());
        
        long eventCount = item.getDomainEvents().stream()
            .filter(e -> e instanceof StockReserved)
            .count();
        assertEquals(1, eventCount);
    }

    @Test
    void reserve_ThrowsWhenInsufficientStock() {
        InventoryItem item = InventoryItem.reconstitute(UUID.randomUUID(), UUID.randomUUID(), 10, 8); // 2 available

        assertThrows(InventoryException.class, () -> item.reserve(new Quantity(3), "TEST-REF"));
    }

    @Test
    void release_DecreasesReservedAndIncreasesAvailable() {
        InventoryItem item = InventoryItem.reconstitute(UUID.randomUUID(), UUID.randomUUID(), 10, 5);

        item.release(new Quantity(3), "TEST-REF");

        assertEquals(10, item.getOnHand().value());
        assertEquals(2, item.getReserved().value());
        assertEquals(8, item.getAvailable().value());
    }
    
    @Test
    void commitSale_DecreasesOnHandAndReserved() {
        InventoryItem item = InventoryItem.reconstitute(UUID.randomUUID(), UUID.randomUUID(), 10, 5);

        item.commitSale(new Quantity(3), "TEST-REF");

        assertEquals(7, item.getOnHand().value());
        assertEquals(2, item.getReserved().value());
        assertEquals(5, item.getAvailable().value());
        
        long eventCount = item.getDomainEvents().stream()
            .filter(e -> e instanceof StockDeducted)
            .count();
        assertEquals(1, eventCount);
    }
}
