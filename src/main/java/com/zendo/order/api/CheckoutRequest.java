package com.zendo.order.api;

public record CheckoutRequest(
        String customerId,
        String idempotencyKey
) {}
