package com.zendo.order.api;

import com.zendo.order.application.CheckoutUseCases;
import com.zendo.order.domain.OrderException;
import com.zendo.order.domain.ParentOrder;
import com.zendo.shared.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CheckoutUseCases checkoutUseCases;

    public OrderController(CheckoutUseCases checkoutUseCases) {
        this.checkoutUseCases = checkoutUseCases;
    }

    record CheckoutRequest(String idempotencyKey, String couponCode) {}

    @PostMapping("/checkout")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> checkout(@RequestBody CheckoutRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            return ResponseEntity.badRequest().body("Idempotency Key is required");
        }
        
        String customerId = user.getUserId();
        ParentOrder order = checkoutUseCases.checkout(customerId, request.idempotencyKey(), request.couponCode());
        
        return ResponseEntity.ok(Map.of(
                "orderId", order.getId().toString(),
                "status", order.getStatus().name(),
                "totalAmount", order.getTotalAmount(),
                "currency", order.getCurrency()
        ));
    }

    @ExceptionHandler(OrderException.class)
    public ResponseEntity<?> handleOrderException(OrderException e) {
        if (e.getMessage().contains("Duplicate checkout")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
