package com.zendo.inventory.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_movements", schema = "inventory")
public class InventoryMovementEntity {
    
    @Id
    private UUID id;
    
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    
    @Column(nullable = false)
    private String type;
    
    @Column(nullable = false)
    private int quantity;
    
    @Column(name = "reference_id", nullable = false)
    private String referenceId;
    
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    
    protected InventoryMovementEntity() {}
    
    public InventoryMovementEntity(UUID id, UUID productId, String type, int quantity, String referenceId) {
        this.id = id;
        this.productId = productId;
        this.type = type;
        this.quantity = quantity;
        this.referenceId = referenceId;
        this.createdAt = Instant.now();
    }
}
