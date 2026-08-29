package com.zendo.promotion.application;

import com.zendo.promotion.domain.FlashSale;
import com.zendo.promotion.domain.FlashSaleException;
import com.zendo.promotion.domain.FlashSaleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class FlashSaleUseCases {

    private final FlashSaleRepository flashSaleRepository;

    public FlashSaleUseCases(FlashSaleRepository flashSaleRepository) {
        this.flashSaleRepository = flashSaleRepository;
    }

    @Transactional
    public FlashSale createFlashSale(UUID vendorId, UUID productId, String sku, BigDecimal flashPrice, int allocatedQuantity, Instant startTime, Instant endTime) {
        FlashSale flashSale = new FlashSale(UUID.randomUUID(), vendorId, productId, sku, flashPrice, allocatedQuantity, startTime, endTime);
        flashSaleRepository.save(flashSale);
        return flashSale;
    }

    @Transactional
    public void activateFlashSale(UUID id) {
        FlashSale flashSale = flashSaleRepository.findById(id)
                .orElseThrow(() -> new FlashSaleException("Flash sale not found"));
        flashSale.activate();
        flashSaleRepository.save(flashSale);
    }
    
    @Transactional
    public void cancelFlashSale(UUID id) {
        FlashSale flashSale = flashSaleRepository.findById(id)
                .orElseThrow(() -> new FlashSaleException("Flash sale not found"));
        flashSale.cancel();
        flashSaleRepository.save(flashSale);
    }
}
