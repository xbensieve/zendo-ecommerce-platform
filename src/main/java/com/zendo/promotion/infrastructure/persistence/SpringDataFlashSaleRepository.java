package com.zendo.promotion.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SpringDataFlashSaleRepository extends JpaRepository<FlashSaleEntity, UUID> {
    
    @Modifying(clearAutomatically = true)
    @Query("UPDATE FlashSaleEntity f SET f.availableQuantity = f.availableQuantity - :qty, f.updatedAt = CURRENT_TIMESTAMP WHERE f.id = :id AND :qty > 0 AND f.availableQuantity >= :qty AND f.status = 'ACTIVE' AND CURRENT_TIMESTAMP BETWEEN f.startTime AND f.endTime")
    int atomicReserveAllocation(@Param("id") UUID id, @Param("qty") int qty);
}
