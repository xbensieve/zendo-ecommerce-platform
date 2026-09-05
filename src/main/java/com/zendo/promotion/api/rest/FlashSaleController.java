package com.zendo.promotion.api.rest;

import com.zendo.promotion.application.FlashSaleUseCases;
import com.zendo.promotion.domain.FlashSale;
import com.zendo.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/flash-sales")
public class FlashSaleController {

    private final FlashSaleUseCases flashSaleUseCases;

    public FlashSaleController(FlashSaleUseCases flashSaleUseCases) {
        this.flashSaleUseCases = flashSaleUseCases;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<FlashSaleResponse> createFlashSale(@Valid @RequestBody CreateFlashSaleRequest request) {
        FlashSale flashSale = flashSaleUseCases.createFlashSale(
                request.vendorId(),
                request.productId(),
                request.sku(),
                request.flashPrice(),
                request.allocatedQuantity(),
                request.startTime(),
                request.endTime()
        );
        return ApiResponse.success(new FlashSaleResponse(flashSale.getId(), flashSale.getStatus().name()));
    }

    public record UpdateStatusRequest(
            @NotBlank @Pattern(regexp = "(?i)^(ACTIVE|CANCELLED)$", message = "Status must be ACTIVE or CANCELLED")
            String status
    ) {}

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
        switch (request.status().toUpperCase()) {
            case "ACTIVE":
                flashSaleUseCases.activateFlashSale(id);
                break;
            case "CANCELLED":
                flashSaleUseCases.cancelFlashSale(id);
                break;
            default:
                return ResponseEntity.badRequest().body(ApiResponse.error("Invalid status: " + request.status()));
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    public record CreateFlashSaleRequest(
            @NotNull UUID vendorId,
            @NotNull UUID productId,
            @NotBlank @Size(max = 64) String sku,
            @NotNull @DecimalMin("0.01") BigDecimal flashPrice,
            @Positive @Max(1000000) int allocatedQuantity,
            @NotNull Instant startTime,
            @NotNull Instant endTime
    ) {}

    public record FlashSaleResponse(UUID id, String status) {}
}
