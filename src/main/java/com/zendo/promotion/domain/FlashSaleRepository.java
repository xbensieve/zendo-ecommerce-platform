package com.zendo.promotion.domain;

import java.util.Optional;
import java.util.UUID;

public interface FlashSaleRepository {
    void save(FlashSale flashSale);
    Optional<FlashSale> findById(UUID id);
    
    /**
     * Atomically reserve flash sale allocation using database conditional update.
     * @return true if successful, false if allocation is insufficient or sale not found.
     */
    boolean atomicReserveAllocation(UUID id, int quantity);
}
