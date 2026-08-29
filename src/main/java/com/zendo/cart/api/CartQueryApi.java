package com.zendo.cart.api;

import java.util.List;
import java.util.Optional;

public interface CartQueryApi {
    Optional<CartDetails> getActiveCartDetails(String customerId);

    record CartDetails(
            String id,
            String customerId,
            String status,
            List<CartItemDetails> items
    ) {}

    record CartItemDetails(
            String vendorId,
            String productId,
            String sku,
            int quantity
    ) {}
}
