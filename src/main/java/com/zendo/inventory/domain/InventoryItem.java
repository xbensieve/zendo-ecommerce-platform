package com.zendo.inventory.domain;

import com.zendo.shared.messaging.DomainEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class InventoryItem {
    private final UUID productId;
    private final UUID vendorId;
    private Quantity onHand;
    private Quantity reserved;
    private Quantity available;
    private final List<DomainEvent> domainEvents;

    private InventoryItem(UUID productId, UUID vendorId, Quantity onHand, Quantity reserved) {
        this.productId = productId;
        this.vendorId = vendorId;
        this.onHand = onHand;
        this.reserved = reserved;
        this.available = onHand.subtract(reserved);
        this.domainEvents = new ArrayList<>();
    }

    public static InventoryItem reconstitute(UUID productId, UUID vendorId, int onHand, int reserved) {
        return new InventoryItem(productId, vendorId, new Quantity(onHand), new Quantity(reserved));
    }

    public static InventoryItem initialize(UUID productId, UUID vendorId) {
        return new InventoryItem(productId, vendorId, new Quantity(0), new Quantity(0));
    }

    public void adjustStock(Quantity newOnHand, String referenceId) {
        if (newOnHand.value() < reserved.value()) {
            throw new InventoryException(String.format("Cannot adjust stock to %d when %d is reserved", newOnHand.value(), reserved.value()));
        }
        this.onHand = newOnHand;
        recalculateAvailable();
        // omitting StockAdjusted event to save time, but can be added if needed
    }

    // Reservation via aggregate (used conceptually for domain logic testing)
    // The actual high-concurrency reservation is done atomically in the repository
    public void reserve(Quantity qty, String referenceId) {
        if (available.value() < qty.value()) {
            throw new InventoryException("Insufficient stock available for reservation");
        }
        this.reserved = this.reserved.add(qty);
        recalculateAvailable();
        registerEvent(new StockReserved(UUID.randomUUID(), Instant.now(), productId.toString(), qty.value(), referenceId));
    }

    public void release(Quantity qty, String referenceId) {
        if (reserved.value() < qty.value()) {
            // Depending on idempotency, might just release what is there or ignore.
            // Strict domain check:
            throw new InventoryException("Cannot release more stock than reserved");
        }
        this.reserved = this.reserved.subtract(qty);
        recalculateAvailable();
    }

    public void commitSale(Quantity qty, String referenceId) {
        if (reserved.value() < qty.value()) {
            throw new InventoryException("Cannot commit sale for stock that was not reserved");
        }
        this.reserved = this.reserved.subtract(qty);
        this.onHand = this.onHand.subtract(qty);
        recalculateAvailable();
        registerEvent(new StockDeducted(UUID.randomUUID(), Instant.now(), productId.toString(), qty.value(), referenceId));
    }

    private void recalculateAvailable() {
        this.available = this.onHand.subtract(this.reserved);
    }

    public UUID getProductId() { return productId; }
    public UUID getVendorId() { return vendorId; }
    public Quantity getOnHand() { return onHand; }
    public Quantity getReserved() { return reserved; }
    public Quantity getAvailable() { return available; }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }
    public void clearDomainEvents() {
        this.domainEvents.clear();
    }
    private void registerEvent(DomainEvent event) {
        this.domainEvents.add(event);
    }
}
