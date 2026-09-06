package com.zendo.review.application;

import com.zendo.shared.exception.DomainException;

public class ReviewException extends DomainException {
    public ReviewException(String message) {
        super(message);
    }
}
