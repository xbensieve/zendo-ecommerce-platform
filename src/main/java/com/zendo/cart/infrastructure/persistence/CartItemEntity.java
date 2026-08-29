package com.zendo.cart.infrastructure.persistence;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "cart_items", schema = "cart")
public class CartItemEntity {
    
    @Id
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private CartEntity cart;
    
    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;
    
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    
    @Column(nullable = false)
    private String sku;
    
    @Column(nullable = false)
    private int quantity;
    
    protected CartItemEntity() {}
    
    public CartItemEntity(UUID id, UUID vendorId, UUID productId, String sku, int quantity) {
        this.id = id;
        this.vendorId = vendorId;
        this.productId = productId;
        this.sku = sku;
        this.quantity = quantity;
    }
    
    public void setCart(CartEntity cart) {
        this.cart = cart;
    }
    
    public void updateQuantity(int quantity) {
        this.quantity = quantity;
    }
    
    public UUID getId() { return id; }
    public UUID getVendorId() { return vendorId; }
    public UUID getProductId() { return productId; }
    public String getSku() { return sku; }
    public int getQuantity() { return quantity; }
}
