package com.zendo.promotion.infrastructure.persistence;

import com.zendo.promotion.domain.FlashSale;
import com.zendo.promotion.domain.FlashSaleRepository;
import com.zendo.promotion.domain.FlashSaleStatus;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcFlashSaleRepository implements FlashSaleRepository {
    
    private final SpringDataFlashSaleRepository jpaRepository;

    public JdbcFlashSaleRepository(SpringDataFlashSaleRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(FlashSale flashSale) {
        FlashSaleEntity entity = jpaRepository.findById(flashSale.getId()).orElseGet(FlashSaleEntity::new);
        entity.setId(flashSale.getId());
        entity.setVendorId(flashSale.getVendorId());
        entity.setProductId(flashSale.getProductId());
        entity.setSku(flashSale.getSku());
        entity.setFlashPrice(flashSale.getFlashPrice());
        entity.setAllocatedQuantity(flashSale.getAllocatedQuantity());
        entity.setAvailableQuantity(flashSale.getAvailableQuantity());
        entity.setStatus(flashSale.getStatus().name());
        entity.setStartTime(flashSale.getStartTime());
        entity.setEndTime(flashSale.getEndTime());
        entity.setUpdatedAt(java.time.Instant.now());
        
        jpaRepository.save(entity);
    }

    @Override
    public Optional<FlashSale> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean atomicReserveAllocation(UUID id, int quantity) {
        return jpaRepository.atomicReserveAllocation(id, quantity) > 0;
    }
    
    private FlashSale toDomain(FlashSaleEntity entity) {
        return new FlashSale(
                entity.getId(),
                entity.getVendorId(),
                entity.getProductId(),
                entity.getSku(),
                entity.getFlashPrice(),
                entity.getAllocatedQuantity(),
                entity.getAvailableQuantity(),
                FlashSaleStatus.valueOf(entity.getStatus()),
                entity.getStartTime(),
                entity.getEndTime()
        );
    }
}
