package com.zendo.inventory.infrastructure.persistence;

import com.zendo.inventory.domain.InventoryItem;
import com.zendo.inventory.domain.InventoryRepository;
import com.zendo.inventory.domain.MovementType;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class InventoryRepositoryImpl implements InventoryRepository {
    
    private final SpringDataInventoryItemRepository itemRepository;
    private final SpringDataInventoryMovementRepository movementRepository;

    public InventoryRepositoryImpl(SpringDataInventoryItemRepository itemRepository, SpringDataInventoryMovementRepository movementRepository) {
        this.itemRepository = itemRepository;
        this.movementRepository = movementRepository;
    }

    @Override
    public void save(InventoryItem item) {
        InventoryItemEntity entity = itemRepository.findById(item.getProductId())
                .orElseGet(() -> new InventoryItemEntity(
                        item.getProductId(),
                        item.getVendorId(),
                        item.getOnHand().value(),
                        item.getReserved().value(),
                        item.getAvailable().value()
                ));
        
        entity.updateStock(item.getOnHand().value(), item.getReserved().value(), item.getAvailable().value());
        itemRepository.save(entity);
    }

    @Override
    public Optional<InventoryItem> findByProductId(UUID productId) {
        return itemRepository.findById(productId)
                .map(entity -> InventoryItem.reconstitute(
                        entity.getProductId(),
                        entity.getVendorId(),
                        entity.getOnHandQuantity(),
                        entity.getReservedQuantity()
                ));
    }

    @Override
    public boolean atomicReserveStock(UUID productId, int quantity, String referenceId) {
        int updatedRows = itemRepository.atomicReserveStock(productId, quantity);
        if (updatedRows > 0) {
            recordMovement(productId, MovementType.RESERVATION, quantity, referenceId);
            return true;
        }
        return false;
    }

    @Override
    public boolean atomicReleaseStock(UUID productId, int quantity, String referenceId) {
        int updatedRows = itemRepository.atomicReleaseStock(productId, quantity);
        if (updatedRows > 0) {
            recordMovement(productId, MovementType.RELEASE, quantity, referenceId);
            return true;
        }
        return false;
    }

    @Override
    public boolean atomicCommitSale(UUID productId, int quantity, String referenceId) {
        int updatedRows = itemRepository.atomicCommitSale(productId, quantity);
        if (updatedRows > 0) {
            recordMovement(productId, MovementType.SALE, quantity, referenceId);
            return true;
        }
        return false;
    }

    @Override
    public void recordMovement(UUID productId, MovementType type, int quantity, String referenceId) {
        InventoryMovementEntity movement = new InventoryMovementEntity(
                UUID.randomUUID(),
                productId,
                type.name(),
                quantity,
                referenceId
        );
        movementRepository.save(movement);
    }
}
