package com.zendo.inventory.application;

import com.zendo.catalog.api.CatalogQueryApi;
import com.zendo.inventory.domain.InventoryException;
import com.zendo.inventory.domain.InventoryItem;
import com.zendo.inventory.domain.InventoryRepository;
import com.zendo.inventory.domain.Quantity;
import com.zendo.shared.messaging.EventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;

@Service
public class InventoryUseCases {

    private final InventoryRepository inventoryRepository;
    private final EventPublisher eventPublisher;
    private final CatalogQueryApi catalogQueryApi;
    private final MeterRegistry meterRegistry;

    public InventoryUseCases(InventoryRepository inventoryRepository, EventPublisher eventPublisher, CatalogQueryApi catalogQueryApi, MeterRegistry meterRegistry) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
        this.catalogQueryApi = catalogQueryApi;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void createInventoryItem(String vendorId, String sku) {
        // Validation with Catalog
        if (!catalogQueryApi.isProductVariantActive(vendorId, sku)) {
            throw new InventoryException("Invalid or inactive product variant: " + sku);
        }
        
        var variantInfo = catalogQueryApi.getVariantInfo(vendorId, sku)
                .orElseThrow(() -> new InventoryException("Product variant not found"));
        
        UUID productId = UUID.fromString(variantInfo.productId());
        UUID vId = UUID.fromString(variantInfo.vendorId());
        
        // Ensure doesn't already exist
        if (inventoryRepository.findByProductId(productId).isPresent()) {
            throw new InventoryException("Inventory item already exists for product: " + productId);
        }

        InventoryItem item = InventoryItem.initialize(productId, vId);
        inventoryRepository.save(item);
        
        item.getDomainEvents().forEach(eventPublisher::publish);
    }

    @Transactional
    public void adjustInventory(UUID productId, int newOnHandQty, String referenceId) {
        InventoryItem item = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryException("Inventory item not found"));
        
        item.adjustStock(new Quantity(newOnHandQty), referenceId);
        inventoryRepository.save(item);
        
        inventoryRepository.recordMovement(productId, com.zendo.inventory.domain.MovementType.ADJUSTMENT, newOnHandQty, referenceId);
        
        item.getDomainEvents().forEach(eventPublisher::publish);
    }

    @Transactional
    public boolean reserveInventory(UUID productId, int quantity, String referenceId) {
        meterRegistry.counter("inventory_reservation_attempts_total").increment();
        
        if (quantity <= 0) {
            meterRegistry.counter("inventory_reservation_rejected_total", "reason", "INVALID_QUANTITY").increment();
            throw new InventoryException("Reservation quantity must be positive");
        }
        
        boolean reserved = inventoryRepository.atomicReserveStock(productId, quantity, referenceId);
        
        if (reserved) {
            meterRegistry.counter("inventory_reservation_success_total").increment();
            // Because the update was atomic in SQL, we must publish the event explicitly.
            // Ideally we'd fetch the entity to publish domain events, but for performance, 
            // we can construct the event directly here or use a helper on the aggregate.
            eventPublisher.publish(new com.zendo.inventory.domain.StockReserved(
                    UUID.randomUUID(), java.time.Instant.now(), productId.toString(), quantity, referenceId
            ));
        } else {
            meterRegistry.counter("inventory_reservation_rejected_total", "reason", "INSUFFICIENT_STOCK").increment();
        }
        
        return reserved;
    }

    @Transactional
    public void releaseInventory(UUID productId, int quantity, String referenceId) {
        if (quantity <= 0) {
            throw new InventoryException("Release quantity must be positive");
        }
        
        boolean released = inventoryRepository.atomicReleaseStock(productId, quantity, referenceId);
        
        if (!released) {
            throw new InventoryException("Failed to release inventory: insufficient reserved quantity or item not found");
        }
        
        // Publish event
        eventPublisher.publish(new com.zendo.inventory.domain.StockReserved(
                UUID.randomUUID(), java.time.Instant.now(), productId.toString(), -quantity, referenceId
        ));
    }

    @Transactional
    public void commitInventorySale(UUID productId, int quantity, String referenceId) {
        if (quantity <= 0) {
            throw new InventoryException("Commit quantity must be positive");
        }
        
        boolean committed = inventoryRepository.atomicCommitSale(productId, quantity, referenceId);
        
        if (!committed) {
            throw new InventoryException("Failed to commit sale: insufficient reserved quantity or item not found");
        }
        
        // Publish event
        eventPublisher.publish(new com.zendo.inventory.domain.StockDeducted(
                UUID.randomUUID(), java.time.Instant.now(), productId.toString(), quantity, referenceId
        ));
    }
}
