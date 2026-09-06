package com.zendo.catalog.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    @DisplayName("Money should normalize amount to 2 decimal places with HALF_UP rounding")
    void shouldNormalizeScale() {
        Money m1 = new Money(new BigDecimal("10"), "USD");
        assertEquals(new BigDecimal("10.00"), m1.amount());

        Money m2 = new Money(new BigDecimal("10.555"), "USD");
        assertEquals(new BigDecimal("10.56"), m2.amount());

        Money m3 = new Money(new BigDecimal("10.554"), "USD");
        assertEquals(new BigDecimal("10.55"), m3.amount());
    }

    @Test
    @DisplayName("Money equals and hashCode should match even when constructed with different BigDecimal scales")
    void shouldBeEqualAcrossDifferentScales() {
        Money m1 = new Money(new BigDecimal("10"), "USD");
        Money m2 = new Money(new BigDecimal("10.00"), "USD");
        Money m3 = Money.of("10.000", "USD");

        assertEquals(m1, m2);
        assertEquals(m2, m3);
        assertEquals(m1.hashCode(), m2.hashCode());
        assertEquals(m2.hashCode(), m3.hashCode());
    }

    @Test
    @DisplayName("Money should normalize currency string to uppercase and trimmed")
    void shouldNormalizeCurrency() {
        Money m = new Money(new BigDecimal("25.00"), " usd ");
        assertEquals("USD", m.currency());
    }

    @Test
    @DisplayName("add should correctly sum two Money instances with same currency")
    void shouldAddMoney() {
        Money m1 = Money.of("15.50", "USD");
        Money m2 = Money.of("10.25", "USD");

        Money sum = m1.add(m2);
        assertEquals(new BigDecimal("25.75"), sum.amount());
        assertEquals("USD", sum.currency());
    }

    @Test
    @DisplayName("subtract should correctly subtract Money and prevent negative result")
    void shouldSubtractMoney() {
        Money m1 = Money.of("20.00", "USD");
        Money m2 = Money.of("7.50", "USD");

        Money diff = m1.subtract(m2);
        assertEquals(new BigDecimal("12.50"), diff.amount());

        assertThrows(IllegalArgumentException.class, () -> m2.subtract(m1));
    }

    @Test
    @DisplayName("add and subtract should throw exception when currencies mismatch")
    void shouldThrowOnCurrencyMismatch() {
        Money usd = Money.of("10.00", "USD");
        Money eur = Money.of("10.00", "EUR");

        assertThrows(IllegalArgumentException.class, () -> usd.add(eur));
        assertThrows(IllegalArgumentException.class, () -> usd.subtract(eur));
        assertThrows(IllegalArgumentException.class, () -> usd.isGreaterThan(eur));
    }

    @Test
    @DisplayName("multiply should scale amount correctly by integer and BigDecimal factor")
    void shouldMultiplyMoney() {
        Money m = Money.of("12.50", "USD");

        Money byInt = m.multiply(3);
        assertEquals(new BigDecimal("37.50"), byInt.amount());

        Money byFactor = m.multiply(new BigDecimal("1.5"));
        assertEquals(new BigDecimal("18.75"), byFactor.amount());
    }

    @Test
    @DisplayName("isGreaterThan and isLessThan should compare monetary amounts accurately")
    void shouldCompareMoney() {
        Money smaller = Money.of("5.00", "USD");
        Money larger = Money.of("10.00", "USD");

        assertTrue(larger.isGreaterThan(smaller));
        assertFalse(smaller.isGreaterThan(larger));
        assertTrue(smaller.isLessThan(larger));
    }

    @Test
    @DisplayName("Constructor should reject negative amount and null or empty currency")
    void shouldRejectInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> new Money(new BigDecimal("-1.00"), "USD"));
        assertThrows(IllegalArgumentException.class, () -> new Money(null, "USD"));
        assertThrows(IllegalArgumentException.class, () -> new Money(BigDecimal.TEN, null));
        assertThrows(IllegalArgumentException.class, () -> new Money(BigDecimal.TEN, "   "));
    }
}
