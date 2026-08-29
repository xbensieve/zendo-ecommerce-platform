package com.zendo.catalog.domain;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) {
    public Money {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        if (currency == null || currency.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency must be specified");
        }
        // Simplified multi-currency support - force uppercase
        currency = currency.trim().toUpperCase();
    }
}
