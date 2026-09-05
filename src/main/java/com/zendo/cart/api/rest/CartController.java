package com.zendo.cart.api.rest;

import com.zendo.cart.application.CartUseCases;
import com.zendo.cart.domain.Cart;
import com.zendo.shared.security.AuthenticatedUser;
import com.zendo.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/carts")
public class CartController {

    private final CartUseCases useCases;

    public CartController(CartUseCases useCases) {
        this.useCases = useCases;
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<Cart>> getActiveCart(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(ApiResponse.success(useCases.getOrCreateActiveCart(user.getUserId())));
    }

    @PostMapping("/me/items")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<Void>> addItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AddItemRequest request) {
        useCases.addItemToCart(user.getUserId(), request.vendorId(), request.sku(), request.quantity());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/me/items/{sku}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<Void>> removeItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable @NotBlank @Size(max = 64) String sku) {
        useCases.removeItemFromCart(user.getUserId(), sku);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/me/items/{sku}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<Void>> updateQuantity(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable @NotBlank @Size(max = 64) String sku,
            @Valid @RequestBody UpdateQuantityRequest request) {
        useCases.updateItemQuantity(user.getUserId(), sku, request.quantity());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/me/checkout")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<Void>> checkout(@AuthenticationPrincipal AuthenticatedUser user) {
        useCases.checkoutCart(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    public record AddItemRequest(
            @NotBlank @Size(max = 64) String vendorId,
            @NotBlank @Size(max = 64) String sku,
            @Positive @Max(999) int quantity
    ) {}

    public record UpdateQuantityRequest(
            @Positive @Max(999) int quantity
    ) {}
}
