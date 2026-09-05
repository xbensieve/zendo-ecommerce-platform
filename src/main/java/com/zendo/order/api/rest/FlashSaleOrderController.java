package com.zendo.order.api.rest;

import com.zendo.order.application.FlashSaleCheckoutUseCases;
import com.zendo.order.domain.ParentOrder;
import com.zendo.shared.api.ApiResponse;
import com.zendo.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders/flash-sales")
public class FlashSaleOrderController {

    private final FlashSaleCheckoutUseCases flashSaleCheckoutUseCases;

    public FlashSaleOrderController(FlashSaleCheckoutUseCases flashSaleCheckoutUseCases) {
        this.flashSaleCheckoutUseCases = flashSaleCheckoutUseCases;
    }

    @PostMapping("/{flashSaleId}/purchase")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<FlashSaleOrderResponse> purchaseFlashSale(
            @PathVariable UUID flashSaleId,
            @Valid @RequestBody FlashSalePurchaseRequest request,
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        ParentOrder order = flashSaleCheckoutUseCases.checkoutFlashSale(
                user.getUserId(),
                idempotencyKey,
                flashSaleId,
                request.quantity()
        );
        
        return ApiResponse.success(new FlashSaleOrderResponse(order.getId(), order.getStatus().name()));
    }

    public record FlashSalePurchaseRequest(
            @NotNull(message = "Quantity is required")
            @Positive(message = "Quantity must be greater than zero")
            @Max(value = 10, message = "Quantity cannot exceed maximum allowed limit of 10")
            Integer quantity
    ) {}

    public record FlashSaleOrderResponse(UUID orderId, String status) {}
}
