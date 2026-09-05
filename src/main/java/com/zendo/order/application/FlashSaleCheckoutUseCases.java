package com.zendo.order.application;

import com.zendo.inventory.application.InventoryUseCases;
import com.zendo.order.domain.ChildOrder;
import com.zendo.order.domain.OrderException;
import com.zendo.order.domain.OrderItem;
import com.zendo.order.domain.OrderRepository;
import com.zendo.order.domain.ParentOrder;
import com.zendo.promotion.api.PromotionApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class FlashSaleCheckoutUseCases {

    private final OrderRepository orderRepository;
    private final PromotionApi promotionApi;
    private final InventoryUseCases inventoryUseCases;
    private final MeterRegistry meterRegistry;

    public FlashSaleCheckoutUseCases(OrderRepository orderRepository,
                                     PromotionApi promotionApi,
                                     InventoryUseCases inventoryUseCases,
                                     MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.promotionApi = promotionApi;
        this.inventoryUseCases = inventoryUseCases;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public ParentOrder checkoutFlashSale(String customerId, String idempotencyKey, UUID flashSaleId, int quantity) {
        meterRegistry.counter("flash_sale_purchase_attempts_total").increment();

        if (quantity <= 0) {
            meterRegistry.counter("flash_sale_purchase_rejected_total", "reason", "INVALID_QUANTITY").increment();
            throw new OrderException("Flash sale purchase quantity must be positive");
        }
        if (quantity > 10) {
            meterRegistry.counter("flash_sale_purchase_rejected_total", "reason", "EXCEEDED_LIMIT").increment();
            throw new OrderException("Flash sale purchase quantity exceeds maximum allowed limit per order");
        }

        // 1. Idempotency Check (Scoped to customer to prevent cross-user key collision)
        String scopedIdempotencyKey = "fs:" + customerId + ":" + idempotencyKey;
        if (!orderRepository.checkAndSaveIdempotencyKey(scopedIdempotencyKey)) {
            meterRegistry.counter("flash_sale_purchase_rejected_total", "reason", "DUPLICATE_REQUEST").increment();
            throw new OrderException("Duplicate checkout request for idempotency key: " + idempotencyKey);
        }

        // 2. Enforce One Flash Sale Purchase per Customer Limit
        String customerPurchaseKey = "fs_limit:" + flashSaleId + ":" + customerId;
        if (!orderRepository.checkAndSaveIdempotencyKey(customerPurchaseKey)) {
            meterRegistry.counter("flash_sale_purchase_rejected_total", "reason", "CUSTOMER_LIMIT_REACHED").increment();
            throw new OrderException("Customer has already participated in this flash sale");
        }

        // 3. Fetch Flash Sale Details (implicitly checks if it exists)
        PromotionApi.FlashSaleDetails flashSaleDetails = promotionApi.getFlashSale(flashSaleId)
                .orElseThrow(() -> {
                    meterRegistry.counter("flash_sale_purchase_rejected_total", "reason", "NOT_FOUND").increment();
                    return new OrderException("Flash sale not found");
                });

        // 3. Reserve Flash Sale Allocation (Atomic)
        boolean allocationReserved = promotionApi.reserveFlashSaleAllocation(flashSaleId, quantity);
        if (!allocationReserved) {
            meterRegistry.counter("flash_sale_purchase_rejected_total", "reason", "SOLD_OUT").increment();
            throw new OrderException("Insufficient flash sale allocation or sale not active");
        }

        // 4. Reserve Physical Inventory (Atomic)
        UUID parentOrderId = UUID.randomUUID();
        String reservationRef = "FLASH-SALE-ORDER-" + parentOrderId;
        boolean stockReserved = inventoryUseCases.reserveInventory(flashSaleDetails.productId(), quantity, reservationRef);
        if (!stockReserved) {
            meterRegistry.counter("flash_sale_purchase_rejected_total", "reason", "PHYSICAL_STOCK_OUT").increment();
            // Since this runs in a single transaction, throwing an exception rolls back the allocation decrement in promotion.
            throw new OrderException("Failed to reserve physical inventory for product: " + flashSaleDetails.productId());
        }

        // 5. Create OrderItem
        // A flash sale purchase bypasses normal pricing and uses the flash price directly.
        BigDecimal finalUnitPrice = flashSaleDetails.flashPrice();
        BigDecimal lineTotal = finalUnitPrice.multiply(BigDecimal.valueOf(quantity));
        
        OrderItem orderItem = new OrderItem(
                UUID.randomUUID(),
                flashSaleDetails.productId(),
                flashSaleDetails.sku(),
                "Flash Sale Item", // In a real app we'd fetch the product name from catalog, but we keep it simple here or query catalog if needed.
                finalUnitPrice,
                BigDecimal.ZERO, // No further discounts on flash sale
                finalUnitPrice,
                "FLASH_SALE:" + flashSaleId,
                quantity
        );

        // We assume vendorId can be resolved or is tied to the flash sale. 
        UUID vendorId = flashSaleDetails.vendorId();
        
        ChildOrder childOrder = new ChildOrder(
                UUID.randomUUID(),
                vendorId,
                "USD", // Default currency for Flash Sales in MVP
                List.of(orderItem)
        );

        ParentOrder parentOrder = new ParentOrder(
                parentOrderId,
                customerId,
                "FLASH_SALE", // marker for cartId since no cart is involved
                "USD",
                List.of(childOrder)
        );

        // 6. Persist Order
        orderRepository.save(parentOrder);
        
        meterRegistry.counter("flash_sale_purchase_success_total").increment();

        return parentOrder;
    }
}
