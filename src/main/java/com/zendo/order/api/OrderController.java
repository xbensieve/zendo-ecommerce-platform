package com.zendo.order.api;

import com.zendo.order.application.CheckoutUseCases;
import com.zendo.order.domain.OrderException;
import com.zendo.order.domain.ParentOrder;
import com.zendo.shared.security.AuthenticatedUser;
import com.zendo.shared.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CheckoutUseCases checkoutUseCases;
    private final com.zendo.order.application.OrderUseCases orderUseCases;

    public OrderController(CheckoutUseCases checkoutUseCases, com.zendo.order.application.OrderUseCases orderUseCases) {
        this.checkoutUseCases = checkoutUseCases;
        this.orderUseCases = orderUseCases;
    }

    public record CheckoutRequest(
        @NotBlank(message = "Idempotency Key is required")
        String idempotencyKey, 
        
        String couponCode
    ) {}

    @PostMapping("/checkout")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<?>> checkout(@Valid @RequestBody CheckoutRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        
        String customerId = user.getUserId();
        ParentOrder order = checkoutUseCases.checkout(customerId, request.idempotencyKey(), request.couponCode());
        
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "orderId", order.getId().toString(),
                "status", order.getStatus().name(),
                "totalAmount", order.getTotalAmount(),
                "currency", order.getCurrency()
        )));
    }

    @org.springframework.web.bind.annotation.GetMapping("/{orderId}")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<?>> getOrder(
            @org.springframework.web.bind.annotation.PathVariable java.util.UUID orderId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        ParentOrder order = orderUseCases.getOrder(orderId);
        boolean isAdmin = user.getRoles().contains("ADMIN");
        if (!isAdmin && !order.getCustomerId().equals(user.getUserId())) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied: insufficient permissions");
        }
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "orderId", order.getId().toString(),
                "customerId", order.getCustomerId(),
                "status", order.getStatus().name(),
                "totalAmount", order.getTotalAmount(),
                "currency", order.getCurrency()
        )));
    }

    @ExceptionHandler(OrderException.class)
    public ResponseEntity<ApiResponse<?>> handleOrderException(OrderException e) {
        if (e.getMessage().contains("Duplicate checkout")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
        }
        return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
    }
}
