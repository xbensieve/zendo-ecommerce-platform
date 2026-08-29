package com.zendo.order.api.rest;

import com.zendo.order.application.FlashSaleCheckoutUseCases;
import com.zendo.order.domain.ParentOrder;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.zendo.shared.security.AuthenticatedUser;

import java.util.UUID;

@RestController
@RequestMapping("/orders/flash-sales")
public class FlashSaleOrderController {

    private final FlashSaleCheckoutUseCases flashSaleCheckoutUseCases;

    public FlashSaleOrderController(FlashSaleCheckoutUseCases flashSaleCheckoutUseCases) {
        this.flashSaleCheckoutUseCases = flashSaleCheckoutUseCases;
    }

    @PostMapping("/{flashSaleId}/purchase")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public FlashSaleOrderResponse purchaseFlashSale(
            @PathVariable UUID flashSaleId,
            @RequestBody FlashSalePurchaseRequest request,
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        ParentOrder order = flashSaleCheckoutUseCases.checkoutFlashSale(
                user.getUserId(),
                idempotencyKey,
                flashSaleId,
                request.quantity()
        );
        
        return new FlashSaleOrderResponse(order.getId(), order.getStatus().name());
    }

    public record FlashSalePurchaseRequest(
            String customerId,
            int quantity
    ) {}

    public record FlashSaleOrderResponse(UUID orderId, String status) {}
}
