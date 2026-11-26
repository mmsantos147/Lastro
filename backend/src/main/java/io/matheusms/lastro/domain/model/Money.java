package io.matheusms.lastro.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Amount of money in a {@link Currency}.
 * <p>
 * Always normalized to {@value #SCALE} decimal places with {@link RoundingMode#HALF_EVEN}, so intermediate
 * values such as average costs keep their precision and two instances with the same value are always
 * {@code equals} (a raw {@link BigDecimal} considers {@code 1.0} and {@code 1.00} different). Round to cents
 * only when presenting or reporting totals, via {@link #roundedToCents()}.
 * <p>
 * Arithmetic between different currencies is rejected: conversions are explicit and happen before.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public static final int SCALE = 8;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        amount = amount.setScale(SCALE, ROUNDING);
    }

    public static Money of(String amount, Currency currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money brl(String amount) {
        return of(amount, Currency.BRL);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money times(BigDecimal factor) {
        return new Money(amount.multiply(factor), currency);
    }

    public Money times(Quantity quantity) {
        return times(quantity.value());
    }

    public Money dividedBy(BigDecimal divisor) {
        if (divisor.signum() == 0) {
            throw new ArithmeticException("Cannot divide money by zero");
        }
        return new Money(amount.divide(divisor, SCALE, ROUNDING), currency);
    }

    public Money dividedBy(Quantity quantity) {
        return dividedBy(quantity.value());
    }

    public Money roundedToCents() {
        return new Money(amount.setScale(2, ROUNDING), currency);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isIn(Currency currency) {
        return this.currency == currency;
    }

    /** @throws IllegalArgumentException if the currencies differ */
    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        if (currency != other.currency) {
            throw new IllegalArgumentException(
                    "Currency mismatch: %s and %s".formatted(currency, other.currency));
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
