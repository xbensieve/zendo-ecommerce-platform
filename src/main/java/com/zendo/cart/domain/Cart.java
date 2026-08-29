package com.zendo.cart.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class Cart {
    private final UUID id;
    private final String customerId; // String allows for Guest Sessions or UUIDs
    private CartStatus status;
    private final List<CartItem> items;

    private Cart(UUID id, String customerId, CartStatus status, List<CartItem> items) {
        this.id = id;
        this.customerId = customerId;
        this.status = status;
        this.items = items;
    }

    public static Cart initialize(String customerId) {
        return new Cart(UUID.randomUUID(), customerId, CartStatus.ACTIVE, new ArrayList<>());
    }
    
    public static Cart reconstitute(UUID id, String customerId, CartStatus status, List<CartItem> items) {
        return new Cart(id, customerId, status, new ArrayList<>(items));
    }

    public void addItem(UUID vendorId, UUID productId, String sku, int quantity) {
        if (status != CartStatus.ACTIVE) {
            throw new CartException("Cannot add items to a cart that is not ACTIVE");
        }
        
        Optional<CartItem> existingItem = items.stream()
                .filter(i -> i.getSku().equals(sku))
                .findFirst();

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.changeQuantity(item.getQuantity() + quantity);
        } else {
            items.add(new CartItem(UUID.randomUUID(), vendorId, productId, sku, quantity));
        }
    }

    public void removeItem(String sku) {
        if (status != CartStatus.ACTIVE) {
            throw new CartException("Cannot remove items from a cart that is not ACTIVE");
        }
        items.removeIf(i -> i.getSku().equals(sku));
    }

    public void changeItemQuantity(String sku, int newQuantity) {
        if (status != CartStatus.ACTIVE) {
            throw new CartException("Cannot change item quantity in a cart that is not ACTIVE");
        }
        CartItem item = items.stream()
                .filter(i -> i.getSku().equals(sku))
                .findFirst()
                .orElseThrow(() -> new CartException("Item not found in cart"));
        item.changeQuantity(newQuantity);
    }

    public void clear() {
        if (status != CartStatus.ACTIVE) {
            throw new CartException("Cannot clear a cart that is not ACTIVE");
        }
        items.clear();
    }

    public void checkout() {
        if (status != CartStatus.ACTIVE) {
            throw new CartException("Only an ACTIVE cart can be checked out");
        }
        if (items.isEmpty()) {
            throw new CartException("Cannot checkout an empty cart");
        }
        this.status = CartStatus.CHECKED_OUT;
    }

    public UUID getId() { return id; }
    public String getCustomerId() { return customerId; }
    public CartStatus getStatus() { return status; }
    public List<CartItem> getItems() { return Collections.unmodifiableList(items); }
}
