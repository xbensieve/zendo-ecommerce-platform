package com.zendo.cart.domain;

import com.zendo.shared.exception.DomainException;

public class CartException extends DomainException {
    public CartException(String message) {
        super(message);
    }
}
