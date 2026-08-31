package com.zendo.inventory.api.rest;

import com.zendo.inventory.application.InventoryUseCases;
import com.zendo.shared.api.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryUseCases useCases;

    public InventoryController(InventoryUseCases useCases) {
        this.useCases = useCases;
    }

    @PostMapping("/{productId}/adjust")
    public ResponseEntity<ApiResponse<Void>> adjustInventory(@PathVariable UUID productId, @RequestBody AdjustStockRequest request) {
        useCases.adjustInventory(productId, request.newOnHandQty(), request.referenceId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    public record AdjustStockRequest(int newOnHandQty, String referenceId) {}
}
