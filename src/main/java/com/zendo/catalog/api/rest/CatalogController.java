package com.zendo.catalog.api.rest;

import com.zendo.catalog.application.CatalogUseCases;
import com.zendo.shared.api.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/products")
public class CatalogController {

    private final CatalogUseCases catalogUseCases;

    public CatalogController(CatalogUseCases catalogUseCases) {
        this.catalogUseCases = catalogUseCases;
    }

    record CreateProductRequest(String vendorId, String name, String description) {}

    @PostMapping
    public ResponseEntity<ApiResponse<String>> createProduct(@RequestBody CreateProductRequest request) {
        String id = catalogUseCases.createProduct(request.vendorId(), request.name(), request.description());
        return ResponseEntity.ok(ApiResponse.success(id));
    }

    record AddVariantRequest(String sku, BigDecimal priceAmount, String currency) {}

    @PostMapping("/{id}/variants")
    public ResponseEntity<ApiResponse<String>> addVariant(@PathVariable String id, @RequestBody AddVariantRequest request) {
        String variantId = catalogUseCases.addVariant(id, request.sku(), request.priceAmount(), request.currency());
        return ResponseEntity.ok(ApiResponse.success(variantId));
    }

    public record UpdateStatusRequest(String status) {}

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Void>> updateStatus(@PathVariable String id, @RequestBody UpdateStatusRequest request) {
        if (request.status() == null) {
             return ResponseEntity.badRequest().body(ApiResponse.error("Status is required"));
        }
        
        switch (request.status().toUpperCase()) {
            case "PUBLISHED":
                catalogUseCases.publishProduct(id);
                break;
            case "DEACTIVATED":
                catalogUseCases.deactivateProduct(id);
                break;
            case "ARCHIVED":
                catalogUseCases.archiveProduct(id);
                break;
            default:
                return ResponseEntity.badRequest().body(ApiResponse.error("Invalid status: " + request.status()));
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
