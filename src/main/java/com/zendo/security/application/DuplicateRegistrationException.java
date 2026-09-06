package com.zendo.security.application;

import com.zendo.shared.exception.DomainException;

public class DuplicateRegistrationException extends DomainException {
    public DuplicateRegistrationException(String message) {
        super(message);
    }
}
