package com.zendo.inventory.domain;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository {
    void save(InventoryItem item);
    Optional<InventoryItem> findByProductId(UUID productId);

    /**
     * Executes an atomic database UPDATE to reserve stock safely under high concurrency.
     * Returns true if successful (rows affected > 0), false if out of stock.
     */
    boolean atomicReserveStock(UUID productId, int quantity, String referenceId);
    
    boolean atomicReleaseStock(UUID productId, int quantity, String referenceId);
    
    boolean atomicCommitSale(UUID productId, int quantity, String referenceId);
    
    /**
     * Records an inventory movement ledger entry.
     */
    void recordMovement(UUID productId, MovementType type, int quantity, String referenceId);
}
