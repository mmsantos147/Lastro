package io.matheusms.lastro.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Non-negative quantity of an asset.
 * <p>
 * Fractional values are allowed (Tesouro Direto bonds are traded in fractions). Normalized to
 * {@value #SCALE} decimal places with {@link RoundingMode#HALF_EVEN}, so equal values are always {@code equals}.
 */
public record Quantity(BigDecimal value) implements Comparable<Quantity> {

    public static final int SCALE = 8;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    public static final Quantity ZERO = new Quantity(BigDecimal.ZERO);

    public Quantity {
        Objects.requireNonNull(value, "value must not be null");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Quantity must not be negative: " + value.toPlainString());
        }
        value = value.setScale(SCALE, ROUNDING);
    }

    public static Quantity of(String value) {
        return new Quantity(new BigDecimal(value));
    }

    public static Quantity of(long value) {
        return new Quantity(BigDecimal.valueOf(value));
    }

    public Quantity plus(Quantity other) {
        return new Quantity(value.add(other.value));
    }

    /**
     * @throws IllegalArgumentException if {@code other} is greater than this quantity
     */
    public Quantity minus(Quantity other) {
        return new Quantity(value.subtract(other.value));
    }

    public Quantity times(BigDecimal factor) {
        return new Quantity(value.multiply(factor));
    }

    public Quantity dividedBy(BigDecimal divisor) {
        if (divisor.signum() == 0) {
            throw new ArithmeticException("Cannot divide quantity by zero");
        }
        return new Quantity(value.divide(divisor, SCALE, ROUNDING));
    }

    /** Drops the fractional part (e.g. {@code 10.5 -> 10}). */
    public Quantity truncated() {
        return new Quantity(value.setScale(0, RoundingMode.DOWN));
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    public boolean isGreaterThan(Quantity other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(Quantity other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
