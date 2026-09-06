package com.zendo.inventory.domain;

import com.zendo.shared.exception.DomainException;

public class InventoryException extends DomainException {
    public InventoryException(String message) {
        super(message);
    }
}
