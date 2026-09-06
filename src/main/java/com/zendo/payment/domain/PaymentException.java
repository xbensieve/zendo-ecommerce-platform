package com.zendo.payment.domain;

import com.zendo.shared.exception.DomainException;

public class PaymentException extends DomainException {
    public PaymentException(String message) {
        super(message);
    }
}
