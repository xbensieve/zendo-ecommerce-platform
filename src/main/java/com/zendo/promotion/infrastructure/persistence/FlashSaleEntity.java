package com.zendo.promotion.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "flash_sales", schema = "promotion")
public class FlashSaleEntity {
    
    @Id
    private UUID id;
    
    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;
    
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    
    @Column(nullable = false)
    private String sku;
    
    @Column(name = "flash_price", nullable = false)
    private BigDecimal flashPrice;
    
    @Column(name = "allocated_quantity", nullable = false)
    private int allocatedQuantity;
    
    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;
    
    @Column(nullable = false)
    private String status;
    
    @Column(name = "start_time", nullable = false)
    private Instant startTime;
    
    @Column(name = "end_time", nullable = false)
    private Instant endTime;
    
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @Version
    private Long version;

    // Getters and Setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getVendorId() { return vendorId; }
    public void setVendorId(UUID vendorId) { this.vendorId = vendorId; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public BigDecimal getFlashPrice() { return flashPrice; }
    public void setFlashPrice(BigDecimal flashPrice) { this.flashPrice = flashPrice; }

    public int getAllocatedQuantity() { return allocatedQuantity; }
    public void setAllocatedQuantity(int allocatedQuantity) { this.allocatedQuantity = allocatedQuantity; }

    public int getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(int availableQuantity) { this.availableQuantity = availableQuantity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }

    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
