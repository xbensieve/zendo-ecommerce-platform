package com.zendo.cart.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "carts", schema = "cart")
public class CartEntity {
    
    @Id
    private UUID id;
    
    @Column(name = "customer_id", nullable = false)
    private String customerId;
    
    @Column(nullable = false)
    private String status;
    
    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();
    
    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItemEntity> items = new ArrayList<>();

    protected CartEntity() {}
    
    public CartEntity(UUID id, String customerId, String status) {
        this.id = id;
        this.customerId = customerId;
        this.status = status;
    }

    public void updateStatus(String status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void clearItems() {
        this.items.clear();
        this.updatedAt = Instant.now();
    }
    
    public void addItem(CartItemEntity item) {
        this.items.add(item);
        item.setCart(this);
        this.updatedAt = Instant.now();
    }

    public void removeItem(CartItemEntity item) {
        this.items.remove(item);
        item.setCart(null);
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getStatus() { return status; }
    public List<CartItemEntity> getItems() { return items; }
}
