package com.zendo.payment.domain;

public class PaymentException extends RuntimeException {
    public PaymentException(String message) {
        super(message);
    }
}
