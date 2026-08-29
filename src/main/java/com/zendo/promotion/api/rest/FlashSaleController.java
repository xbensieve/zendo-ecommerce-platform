package com.zendo.promotion.api.rest;

import com.zendo.promotion.application.FlashSaleUseCases;
import com.zendo.promotion.domain.FlashSale;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/flash-sales")
public class FlashSaleController {

    private final FlashSaleUseCases flashSaleUseCases;

    public FlashSaleController(FlashSaleUseCases flashSaleUseCases) {
        this.flashSaleUseCases = flashSaleUseCases;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public FlashSaleResponse createFlashSale(@RequestBody CreateFlashSaleRequest request) {
        FlashSale flashSale = flashSaleUseCases.createFlashSale(
                request.vendorId(),
                request.productId(),
                request.sku(),
                request.flashPrice(),
                request.allocatedQuantity(),
                request.startTime(),
                request.endTime()
        );
        return new FlashSaleResponse(flashSale.getId(), flashSale.getStatus().name());
    }

    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    public void activateFlashSale(@PathVariable UUID id) {
        flashSaleUseCases.activateFlashSale(id);
    }
    
    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    public void cancelFlashSale(@PathVariable UUID id) {
        flashSaleUseCases.cancelFlashSale(id);
    }

    public record CreateFlashSaleRequest(
            UUID vendorId,
            UUID productId,
            String sku,
            BigDecimal flashPrice,
            int allocatedQuantity,
            Instant startTime,
            Instant endTime
    ) {}

    public record FlashSaleResponse(UUID id, String status) {}
}
