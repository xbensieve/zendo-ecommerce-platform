package com.zendo.catalog.domain;

import com.zendo.shared.exception.DomainException;

public class CatalogException extends DomainException {
    public CatalogException(String message) {
        super(message);
    }
}
