package com.zendo.inventory.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SpringDataInventoryItemRepository extends JpaRepository<InventoryItemEntity, UUID> {
    
    @Modifying(clearAutomatically = true)
    @Query("UPDATE InventoryItemEntity i SET i.availableQuantity = i.availableQuantity - :qty, i.reservedQuantity = i.reservedQuantity + :qty, i.updatedAt = CURRENT_TIMESTAMP WHERE i.productId = :productId AND i.availableQuantity >= :qty")
    int atomicReserveStock(@Param("productId") UUID productId, @Param("qty") int qty);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE InventoryItemEntity i SET i.availableQuantity = i.availableQuantity + :qty, i.reservedQuantity = i.reservedQuantity - :qty, i.updatedAt = CURRENT_TIMESTAMP WHERE i.productId = :productId AND i.reservedQuantity >= :qty")
    int atomicReleaseStock(@Param("productId") UUID productId, @Param("qty") int qty);
    
    @Modifying(clearAutomatically = true)
    @Query("UPDATE InventoryItemEntity i SET i.onHandQuantity = i.onHandQuantity - :qty, i.reservedQuantity = i.reservedQuantity - :qty, i.updatedAt = CURRENT_TIMESTAMP WHERE i.productId = :productId AND i.reservedQuantity >= :qty")
    int atomicCommitSale(@Param("productId") UUID productId, @Param("qty") int qty);
}
