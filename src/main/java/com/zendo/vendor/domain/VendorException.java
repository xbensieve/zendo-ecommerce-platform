package com.zendo.vendor.domain;

import com.zendo.shared.exception.DomainException;

public class VendorException extends DomainException {
    public VendorException(String message) {
        super(message);
    }
}
