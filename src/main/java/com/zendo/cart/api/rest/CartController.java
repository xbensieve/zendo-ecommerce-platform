package com.zendo.cart.api.rest;

import com.zendo.cart.application.CartUseCases;
import com.zendo.cart.domain.Cart;
import com.zendo.shared.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/carts")
public class CartController {

    private final CartUseCases useCases;

    public CartController(CartUseCases useCases) {
        this.useCases = useCases;
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Cart> getActiveCart(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(useCases.getOrCreateActiveCart(user.getUserId()));
    }

    @PostMapping("/me/items")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> addItem(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody AddItemRequest request) {
        useCases.addItemToCart(user.getUserId(), request.vendorId(), request.sku(), request.quantity());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me/items/{sku}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> removeItem(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String sku) {
        useCases.removeItemFromCart(user.getUserId(), sku);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/me/items/{sku}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> updateQuantity(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String sku, @RequestBody UpdateQuantityRequest request) {
        useCases.updateItemQuantity(user.getUserId(), sku, request.quantity());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/me/checkout")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> checkout(@AuthenticationPrincipal AuthenticatedUser user) {
        useCases.checkoutCart(user.getUserId());
        return ResponseEntity.ok().build();
    }

    public record AddItemRequest(String vendorId, String sku, int quantity) {}
    public record UpdateQuantityRequest(int quantity) {}
}
