package io.matheusms.lastro.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Record of a buy or sell executed by the user outside this application (at the broker or exchange).
 * Trades are the source of truth from which positions are derived; this application never executes them.
 *
 * @param id {@code null} until persisted
 * @param unitPrice price per unit, excluding fees; its currency is the trade's price currency
 * @param fee total fee of the operation (not per unit); when paid in a currency, it must be the price currency
 * @param externalId identifier given by the source (e.g. Binance trade id); {@code null} when not available
 */
public record Trade(
        Long id,
        Long userId,
        Long assetId,
        Long institutionId,
        TradeType type,
        LocalDate date,
        Quantity quantity,
        Money unitPrice,
        TradeFee fee,
        EntrySource source,
        String externalId) {

    public Trade {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(assetId, "assetId must not be null");
        Objects.requireNonNull(institutionId, "institutionId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(quantity, "quantity must not be null");
        Objects.requireNonNull(unitPrice, "unitPrice must not be null");
        Objects.requireNonNull(fee, "fee must not be null");
        Objects.requireNonNull(source, "source must not be null");
        if (quantity.isZero()) {
            throw new IllegalArgumentException("Trade quantity must be positive");
        }
        if (unitPrice.isNegative()) {
            throw new IllegalArgumentException("Trade unit price must not be negative");
        }
        if (fee instanceof TradeFee.InCurrency(Money amount) && amount.currency() != unitPrice.currency()) {
            throw new IllegalArgumentException("Fee currency must match the price currency");
        }
    }

    public Currency priceCurrency() {
        return unitPrice.currency();
    }
}
