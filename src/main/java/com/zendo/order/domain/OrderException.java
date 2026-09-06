package com.zendo.order.domain;

import com.zendo.shared.exception.DomainException;

public class OrderException extends DomainException {
    public OrderException(String message) {
        super(message);
    }

    public OrderException(String message, Throwable cause) {
        super(message, cause);
    }
}
