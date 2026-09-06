package com.zendo.promotion.domain;

import com.zendo.shared.exception.DomainException;

public class PromotionException extends DomainException {
    public PromotionException(String message) {
        super(message);
    }
}
