package com.zendo.inventory.api.rest;

import com.zendo.inventory.application.InventoryUseCases;
import com.zendo.inventory.domain.InventoryException;
import com.zendo.inventory.domain.InventoryItem;
import com.zendo.shared.api.ApiResponse;
import com.zendo.shared.security.AuthenticatedUser;
import com.zendo.vendor.api.VendorQueryApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryUseCases useCases;
    private final VendorQueryApi vendorQueryApi;

    public InventoryController(InventoryUseCases useCases, VendorQueryApi vendorQueryApi) {
        this.useCases = useCases;
        this.vendorQueryApi = vendorQueryApi;
    }

    public record AdjustStockRequest(
        @NotNull(message = "Quantity is required")
        @PositiveOrZero(message = "Quantity must be zero or positive")
        Integer newOnHandQty, 
        
        @Size(max = 255, message = "Reference ID cannot exceed 255 characters")
        String referenceId
    ) {}

    @PostMapping("/{productId}/adjust")
    @PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> adjustInventory(
            @PathVariable UUID productId, 
            @Valid @RequestBody AdjustStockRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        verifyInventoryOwnership(productId, user);
        useCases.adjustInventory(productId, request.newOnHandQty(), request.referenceId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private void verifyInventoryOwnership(UUID productId, AuthenticatedUser user) {
        boolean isAdmin = user.getRoles().contains("ADMIN");
        if (isAdmin) return;
        
        InventoryItem item = useCases.getInventoryItem(productId)
                .orElseThrow(() -> new InventoryException("Inventory item not found for product: " + productId));
        
        if (!vendorQueryApi.isVendorOwner(item.getVendorId().toString(), user.getUserId())) {
            throw new AccessDeniedException("User does not have ownership of the vendor for product inventory: " + productId);
        }
    }
}
