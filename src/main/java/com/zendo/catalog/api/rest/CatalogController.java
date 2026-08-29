package com.zendo.catalog.api.rest;

import com.zendo.catalog.application.CatalogUseCases;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/products")
public class CatalogController {

    private final CatalogUseCases catalogUseCases;

    public CatalogController(CatalogUseCases catalogUseCases) {
        this.catalogUseCases = catalogUseCases;
    }

    record CreateProductRequest(String vendorId, String name, String description) {}

    @PostMapping
    public ResponseEntity<String> createProduct(@RequestBody CreateProductRequest request) {
        String id = catalogUseCases.createProduct(request.vendorId(), request.name(), request.description());
        return ResponseEntity.ok(id);
    }

    record AddVariantRequest(String sku, BigDecimal priceAmount, String currency) {}

    @PostMapping("/{id}/variants")
    public ResponseEntity<String> addVariant(@PathVariable String id, @RequestBody AddVariantRequest request) {
        String variantId = catalogUseCases.addVariant(id, request.sku(), request.priceAmount(), request.currency());
        return ResponseEntity.ok(variantId);
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<Void> publishProduct(@PathVariable String id) {
        catalogUseCases.publishProduct(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateProduct(@PathVariable String id) {
        catalogUseCases.deactivateProduct(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<Void> archiveProduct(@PathVariable String id) {
        catalogUseCases.archiveProduct(id);
        return ResponseEntity.ok().build();
    }
}
