package com.zendo.inventory.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_items", schema = "inventory")
public class InventoryItemEntity {
    
    @Id
    @Column(name = "product_id")
    private UUID productId;
    
    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;
    
    @Column(name = "on_hand_quantity", nullable = false)
    private int onHandQuantity;
    
    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;
    
    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;
    
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    protected InventoryItemEntity() {}
    
    public InventoryItemEntity(UUID productId, UUID vendorId, int onHandQuantity, int reservedQuantity, int availableQuantity) {
        this.productId = productId;
        this.vendorId = vendorId;
        this.onHandQuantity = onHandQuantity;
        this.reservedQuantity = reservedQuantity;
        this.availableQuantity = availableQuantity;
        this.updatedAt = Instant.now();
    }

    public void updateStock(int onHandQuantity, int reservedQuantity, int availableQuantity) {
        this.onHandQuantity = onHandQuantity;
        this.reservedQuantity = reservedQuantity;
        this.availableQuantity = availableQuantity;
        this.updatedAt = Instant.now();
    }

    public UUID getProductId() { return productId; }
    public UUID getVendorId() { return vendorId; }
    public int getOnHandQuantity() { return onHandQuantity; }
    public int getReservedQuantity() { return reservedQuantity; }
    public int getAvailableQuantity() { return availableQuantity; }
}
