package io.matheusms.lastro.domain.model;

import java.util.Objects;

/**
 * Holding of one asset, consolidated across all institutions and derived from its history. Never stored as
 * the source of truth.
 *
 * @param averageCost average acquisition cost per unit in BRL, fees included (Receita Federal rules);
 *     zero when the position is closed
 */
public record Position(Long assetId, Quantity quantity, Money averageCost) {

    public Position {
        Objects.requireNonNull(assetId, "assetId must not be null");
        Objects.requireNonNull(quantity, "quantity must not be null");
        Objects.requireNonNull(averageCost, "averageCost must not be null");
        if (!averageCost.isIn(Currency.BRL)) {
            throw new IllegalArgumentException("Average cost must be in BRL");
        }
    }

    public static Position empty(Long assetId) {
        return new Position(assetId, Quantity.ZERO, Money.zero(Currency.BRL));
    }

    /** Total acquisition cost in BRL: {@code averageCost × quantity}. */
    public Money totalCost() {
        return averageCost.times(quantity);
    }

    public boolean isClosed() {
        return quantity.isZero();
    }
}
