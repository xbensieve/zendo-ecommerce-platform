package com.zendo.inventory.domain;

public record Quantity(int value) {
    public Quantity {
        if (value < 0) {
            throw new InventoryException("Quantity cannot be negative: " + value);
        }
    }
    
    public Quantity add(Quantity other) {
        return new Quantity(this.value + other.value);
    }

    public Quantity subtract(Quantity other) {
        if (this.value < other.value) {
            throw new InventoryException("Cannot subtract quantity: result would be negative");
        }
        return new Quantity(this.value - other.value);
    }
}
