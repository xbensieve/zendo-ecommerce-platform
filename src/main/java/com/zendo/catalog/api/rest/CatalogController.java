package com.zendo.catalog.api.rest;

import com.zendo.catalog.application.CatalogUseCases;
import com.zendo.catalog.domain.CatalogException;
import com.zendo.catalog.domain.Product;
import com.zendo.shared.api.ApiResponse;
import com.zendo.shared.security.AuthenticatedUser;
import com.zendo.vendor.api.VendorQueryApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/products")
public class CatalogController {

    private final CatalogUseCases catalogUseCases;
    private final VendorQueryApi vendorQueryApi;

    public CatalogController(CatalogUseCases catalogUseCases, VendorQueryApi vendorQueryApi) {
        this.catalogUseCases = catalogUseCases;
        this.vendorQueryApi = vendorQueryApi;
    }

    public record CreateProductRequest(
        @NotBlank(message = "Vendor ID is required")
        String vendorId, 
        
        @NotBlank(message = "Product name is required")
        @Size(max = 255, message = "Product name cannot exceed 255 characters")
        String name, 
        
        @Size(max = 2000, message = "Description cannot exceed 2000 characters")
        String description
    ) {}

    @PostMapping
    @PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> createProduct(
            @Valid @RequestBody CreateProductRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        verifyVendorOwnership(request.vendorId(), user);
        String id = catalogUseCases.createProduct(request.vendorId(), request.name(), request.description());
        return ResponseEntity.ok(ApiResponse.success(id));
    }

    public record AddVariantRequest(
        @NotBlank(message = "SKU is required")
        @Size(max = 64, message = "SKU cannot exceed 64 characters")
        String sku, 
        
        @NotNull(message = "Price is required")
        @Positive(message = "Price must be greater than zero")
        BigDecimal priceAmount, 
        
        @NotBlank(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
        String currency
    ) {}

    @PostMapping("/{id}/variants")
    @PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> addVariant(
            @PathVariable String id, 
            @Valid @RequestBody AddVariantRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        verifyProductOwnership(id, user);
        String variantId = catalogUseCases.addVariant(id, request.sku(), request.priceAmount(), request.currency());
        return ResponseEntity.ok(ApiResponse.success(variantId));
    }

    public record UpdateStatusRequest(
        @NotBlank(message = "Status is required")
        String status
    ) {}

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateStatus(
            @PathVariable String id, 
            @Valid @RequestBody UpdateStatusRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        verifyProductOwnership(id, user);

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

    private void verifyVendorOwnership(String vendorId, AuthenticatedUser user) {
        boolean isAdmin = user.getRoles().contains("ADMIN");
        if (!isAdmin && !vendorQueryApi.isVendorOwner(vendorId, user.getUserId())) {
            throw new AccessDeniedException("User does not have ownership of vendor: " + vendorId);
        }
    }

    private void verifyProductOwnership(String productId, AuthenticatedUser user) {
        boolean isAdmin = user.getRoles().contains("ADMIN");
        if (isAdmin) return;
        Product product = catalogUseCases.getProduct(productId)
                .orElseThrow(() -> new CatalogException("Product not found: " + productId));
        if (!vendorQueryApi.isVendorOwner(product.getVendorId().value().toString(), user.getUserId())) {
            throw new AccessDeniedException("User does not have ownership of product: " + productId);
        }
    }
}
