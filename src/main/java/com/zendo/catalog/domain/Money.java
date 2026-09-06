package com.zendo.catalog.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Rich Value Object representing monetary values in the Catalog domain.
 * Enforces standardized scale (2 decimal places) and currency invariants,
 * providing safe arithmetic operations and scale-agnostic value equality.
 */
public record Money(BigDecimal amount, String currency) {

    public static final int DEFAULT_SCALE = 2;
    public static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;

    public Money {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        if (currency == null || currency.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency must be specified");
        }
        amount = amount.setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);
        currency = currency.trim().toUpperCase();
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        ensureSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        ensureSameCurrency(other);
        if (this.amount.compareTo(other.amount) < 0) {
            throw new IllegalArgumentException("Subtraction results in negative amount");
        }
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public Money multiply(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        return new Money(this.amount.multiply(BigDecimal.valueOf(quantity)), this.currency);
    }

    public Money multiply(BigDecimal factor) {
        if (factor == null || factor.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Factor cannot be negative");
        }
        return new Money(this.amount.multiply(factor), this.currency);
    }

    public boolean isGreaterThan(Money other) {
        ensureSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isLessThan(Money other) {
        ensureSameCurrency(other);
        return this.amount.compareTo(other.amount) < 0;
    }

    private void ensureSameCurrency(Money other) {
        if (other == null) {
            throw new IllegalArgumentException("Cannot operate on null Money");
        }
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + this.currency + " vs " + other.currency);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money other)) return false;
        return Objects.equals(currency, other.currency)
                && amount.compareTo(other.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }
}
