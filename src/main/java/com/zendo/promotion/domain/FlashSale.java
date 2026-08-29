package com.zendo.promotion.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class FlashSale {
    private final UUID id;
    private final UUID vendorId;
    private final UUID productId;
    private final String sku;
    private final BigDecimal flashPrice;
    private final int allocatedQuantity;
    private int availableQuantity;
    private FlashSaleStatus status;
    private final Instant startTime;
    private final Instant endTime;

    public FlashSale(UUID id, UUID vendorId, UUID productId, String sku, BigDecimal flashPrice, int allocatedQuantity, Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null || startTime.isAfter(endTime)) {
            throw new FlashSaleException("Invalid validity period for flash sale");
        }
        if (allocatedQuantity <= 0) {
            throw new FlashSaleException("Allocated quantity must be strictly positive");
        }
        if (flashPrice == null || flashPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new FlashSaleException("Flash price must be zero or positive");
        }
        
        this.id = id;
        this.vendorId = vendorId;
        this.productId = productId;
        this.sku = sku;
        this.flashPrice = flashPrice;
        this.allocatedQuantity = allocatedQuantity;
        this.availableQuantity = allocatedQuantity; // Initially all available
        this.status = FlashSaleStatus.DRAFT;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // For reconstitution from DB
    public FlashSale(UUID id, UUID vendorId, UUID productId, String sku, BigDecimal flashPrice, int allocatedQuantity, int availableQuantity, FlashSaleStatus status, Instant startTime, Instant endTime) {
        this.id = id;
        this.vendorId = vendorId;
        this.productId = productId;
        this.sku = sku;
        this.flashPrice = flashPrice;
        this.allocatedQuantity = allocatedQuantity;
        this.availableQuantity = availableQuantity;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void schedule() {
        if (this.status != FlashSaleStatus.DRAFT) {
            throw new FlashSaleException("Only DRAFT flash sales can be scheduled");
        }
        this.status = FlashSaleStatus.SCHEDULED;
    }

    public void activate() {
        if (this.status != FlashSaleStatus.SCHEDULED && this.status != FlashSaleStatus.DRAFT) {
            throw new FlashSaleException("Flash sale cannot be activated from status " + this.status);
        }
        this.status = FlashSaleStatus.ACTIVE;
    }

    public void end() {
        if (this.status != FlashSaleStatus.ACTIVE) {
            throw new FlashSaleException("Only ACTIVE flash sales can be ended");
        }
        this.status = FlashSaleStatus.ENDED;
    }

    public void cancel() {
        if (this.status == FlashSaleStatus.ENDED) {
            throw new FlashSaleException("Cannot cancel an ENDED flash sale");
        }
        this.status = FlashSaleStatus.CANCELLED;
    }

    public void reserve(int quantity) {
        if (status != FlashSaleStatus.ACTIVE) {
            throw new FlashSaleException("Flash sale is not ACTIVE");
        }
        Instant now = Instant.now();
        if (now.isBefore(startTime) || now.isAfter(endTime)) {
            throw new FlashSaleException("Flash sale is not within its validity period");
        }
        if (quantity <= 0) {
            throw new FlashSaleException("Reservation quantity must be positive");
        }
        if (availableQuantity < quantity) {
            throw new FlashSaleException("Insufficient flash sale allocation");
        }
        this.availableQuantity -= quantity;
    }

    public UUID getId() { return id; }
    public UUID getVendorId() { return vendorId; }
    public UUID getProductId() { return productId; }
    public String getSku() { return sku; }
    public BigDecimal getFlashPrice() { return flashPrice; }
    public int getAllocatedQuantity() { return allocatedQuantity; }
    public int getAvailableQuantity() { return availableQuantity; }
    public FlashSaleStatus getStatus() { return status; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
}
