package com.nepopay.common;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Immutable amount of Nigerian naira, stored as whole kobo (1 naira = 100 kobo).
 * Floating point is never used for money: 0.1 + 0.2 != 0.3 in binary floating point.
 * Always non-negative; direction (debit/credit) is expressed by the ledger entry type.
 */
public record Money(long kobo) implements Comparable<Money> {

    public static final Money ZERO = new Money(0);

    public Money {
        if (kobo < 0) {
            throw new IllegalArgumentException("Money cannot be negative");
        }
    }

    public static Money ofKobo(long kobo) {
        return new Money(kobo);
    }

    public static Money ofNaira(long naira) {
        try {
            return new Money(Math.multiplyExact(naira, 100L));
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Amount too large");
        }
    }

    /** Parses a naira amount such as 20000 or 20000.50. Rejects more than 2 decimal places. */
    public static Money fromNaira(BigDecimal naira) {
        if (naira.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Amount cannot have more than 2 decimal places");
        }
        try {
            return new Money(naira.movePointRight(2).longValueExact());
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Invalid or too large amount");
        }
    }

    public Money plus(Money other) {
        try {
            return new Money(Math.addExact(kobo, other.kobo));
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Amount too large");
        }
    }

    public Money minus(Money other) {
        if (other.kobo > kobo) {
            throw new IllegalArgumentException("Result would be negative");
        }
        return new Money(kobo - other.kobo);
    }

    public boolean isZero() {
        return kobo == 0;
    }

    public boolean isGreaterThan(Money other) {
        return kobo > other.kobo;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(kobo, other.kobo);
    }

    /** Display helper, e.g. ₦20,000.00 */
    @Override
    public String toString() {
        return String.format(Locale.US, "₦%,d.%02d", kobo / 100, kobo % 100);
    }
}
