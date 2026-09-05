package com.zendo.order.application;

import com.zendo.cart.api.CartQueryApi;
import com.zendo.cart.application.CartUseCases;
import com.zendo.pricing.api.PricingQueryApi;
import com.zendo.inventory.application.InventoryUseCases;
import com.zendo.order.domain.ChildOrder;
import com.zendo.order.domain.OrderException;
import com.zendo.order.domain.OrderItem;
import com.zendo.order.domain.OrderRepository;
import com.zendo.order.domain.ParentOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CheckoutUseCases {

    private final OrderRepository orderRepository;
    private final CartQueryApi cartQueryApi;
    private final CartUseCases cartUseCases;
    private final PricingQueryApi pricingQueryApi;
    private final com.zendo.promotion.api.PromotionApi promotionApi;
    private final InventoryUseCases inventoryUseCases;
    private final MeterRegistry meterRegistry;

    public CheckoutUseCases(OrderRepository orderRepository,
                            CartQueryApi cartQueryApi,
                            CartUseCases cartUseCases,
                            PricingQueryApi pricingQueryApi,
                            com.zendo.promotion.api.PromotionApi promotionApi,
                            InventoryUseCases inventoryUseCases,
                            MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.cartQueryApi = cartQueryApi;
        this.cartUseCases = cartUseCases;
        this.pricingQueryApi = pricingQueryApi;
        this.promotionApi = promotionApi;
        this.inventoryUseCases = inventoryUseCases;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public ParentOrder checkout(String customerId, String idempotencyKey, String couponCode) {
        meterRegistry.counter("checkout_attempts_total").increment();
        
        // 1. Idempotency Check (Scoped to customer with payload hash)
        String scopedKey = customerId + ":" + idempotencyKey;
        String payloadToHash = customerId + ":" + (couponCode != null ? couponCode : "");
        String payloadHash;
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(payloadToHash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            payloadHash = java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            payloadHash = String.valueOf(payloadToHash.hashCode());
        }

        if (!orderRepository.checkAndSaveIdempotencyKey(scopedKey, payloadHash)) {
            meterRegistry.counter("checkout_rejected_total", "reason", "DUPLICATE_REQUEST").increment();
            throw new OrderException("Duplicate checkout request for idempotency key: " + idempotencyKey);
        }

        // 2. Fetch Cart
        var cartDetails = cartQueryApi.getActiveCartDetails(customerId)
                .orElseThrow(() -> {
                    meterRegistry.counter("checkout_rejected_total", "reason", "CART_NOT_FOUND").increment();
                    return new OrderException("Active cart not found");
                });

        if (cartDetails.items().isEmpty()) {
            meterRegistry.counter("checkout_rejected_total", "reason", "CART_EMPTY").increment();
            throw new OrderException("Cannot checkout an empty cart");
        }

        // Atomically transition cart to CHECKED_OUT within the checkout transaction
        cartUseCases.checkoutCart(customerId);

        UUID parentOrderId = UUID.randomUUID();
        
        // 3. Group by Vendor
        Map<String, List<CartQueryApi.CartItemDetails>> itemsByVendor = cartDetails.items().stream()
                .collect(Collectors.groupingBy(CartQueryApi.CartItemDetails::vendorId));

        List<ChildOrder> childOrders = new ArrayList<>();
        String orderCurrency = null; // We assume a single currency for the whole order in this MVP

        for (var entry : itemsByVendor.entrySet()) {
            UUID vendorId = UUID.fromString(entry.getKey());
            List<OrderItem> orderItems = new ArrayList<>();

            for (var cartItem : entry.getValue()) {
                // 4. Validate with Pricing
                var pricedItem = pricingQueryApi.calculateItemPrice(vendorId.toString(), cartItem.productId(), cartItem.sku(), cartItem.quantity(), couponCode);
                
                if (orderCurrency == null) {
                    orderCurrency = pricedItem.currency();
                } else if (!orderCurrency.equals(pricedItem.currency())) {
                    throw new OrderException("Mixed currencies in cart not supported");
                }

                UUID productId = UUID.fromString(pricedItem.productId());
                
                // 5. Reserve Inventory
                String reservationRef = "ORDER-" + parentOrderId;
                boolean reserved = inventoryUseCases.reserveInventory(productId, cartItem.quantity(), reservationRef);
                if (!reserved) {
                    meterRegistry.counter("checkout_rejected_total", "reason", "INVENTORY_SHORTAGE").increment();
                    throw new OrderException("Failed to reserve inventory for product: " + productId);
                }

                // 6. Create OrderItem
                orderItems.add(new OrderItem(
                        UUID.randomUUID(),
                        productId,
                        pricedItem.sku(),
                        pricedItem.productName(),
                        pricedItem.originalUnitPrice(),
                        pricedItem.appliedDiscount(),
                        pricedItem.finalUnitPrice(),
                        pricedItem.promotionRef(),
                        cartItem.quantity()
                ));
            }

            // 7. Create ChildOrder
            childOrders.add(new ChildOrder(
                    UUID.randomUUID(),
                    vendorId,
                    orderCurrency,
                    orderItems
            ));
        }

        if (orderCurrency == null) {
            throw new OrderException("Order currency could not be determined");
        }

        // 8. Create ParentOrder
        ParentOrder parentOrder = new ParentOrder(
                parentOrderId,
                customerId,
                cartDetails.id(),
                orderCurrency,
                childOrders
        );

        // 9. Redeem Coupon if any
        if (couponCode != null && !couponCode.isBlank()) {
            promotionApi.redeemCoupon(couponCode);
        }

        // 10. Persist Order
        orderRepository.save(parentOrder);
        
        meterRegistry.counter("checkout_success_total").increment();

        return parentOrder;
    }
}
