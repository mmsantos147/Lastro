package io.matheusms.lastro.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Closing price of an asset on a date, in the asset's quote currency.
 */
public record Quote(Long assetId, LocalDate date, Money closePrice, QuoteSource source) {

    public Quote {
        Objects.requireNonNull(assetId, "assetId must not be null");
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(closePrice, "closePrice must not be null");
        Objects.requireNonNull(source, "source must not be null");
        if (closePrice.isNegative()) {
            throw new IllegalArgumentException("Close price must not be negative");
        }
    }
}
