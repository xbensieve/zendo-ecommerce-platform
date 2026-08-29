package com.zendo.order.domain;

public enum OrderStatus {
    PENDING,
    PAYMENT_PENDING,
    PAYMENT_AUTHORIZED,
    PAYMENT_FAILED,
    PAID,
    CONFIRMED,
    READY_FOR_FULFILLMENT,
    CANCELLED
}
