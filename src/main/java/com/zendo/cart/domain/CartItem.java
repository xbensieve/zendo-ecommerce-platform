package com.zendo.cart.domain;

import java.util.UUID;

public class CartItem {
    private final UUID id;
    private final UUID vendorId;
    private final UUID productId;
    private final String sku;
    private int quantity;

    public CartItem(UUID id, UUID vendorId, UUID productId, String sku, int quantity) {
        if (quantity <= 0) {
            throw new CartException("Cart item quantity must be greater than zero");
        }
        this.id = id;
        this.vendorId = vendorId;
        this.productId = productId;
        this.sku = sku;
        this.quantity = quantity;
    }

    public void changeQuantity(int newQuantity) {
        if (newQuantity <= 0) {
            throw new CartException("Cart item quantity must be greater than zero");
        }
        this.quantity = newQuantity;
    }

    public UUID getId() { return id; }
    public UUID getVendorId() { return vendorId; }
    public UUID getProductId() { return productId; }
    public String getSku() { return sku; }
    public int getQuantity() { return quantity; }
}
