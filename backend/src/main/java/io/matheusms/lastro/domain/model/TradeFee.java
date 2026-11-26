package io.matheusms.lastro.domain.model;

import java.util.Objects;

/**
 * Cost of a trade (brokerage, B3 or exchange fees). Paid either in a currency or in units of an asset
 * (e.g. BNB on Binance, or part of the crypto just bought).
 */
public sealed interface TradeFee {

    static TradeFee none(Currency currency) {
        return new InCurrency(Money.zero(currency));
    }

    record InCurrency(Money amount) implements TradeFee {

        public InCurrency {
            Objects.requireNonNull(amount, "amount must not be null");
            if (amount.isNegative()) {
                throw new IllegalArgumentException("Fee must not be negative");
            }
        }
    }

    /**
     * Fee paid in units of an asset. Its BRL value enters the trade cost and the quantity is taken from the
     * fee asset's position; both are resolved by the currency conversion step before position calculation.
     */
    record InAsset(Long assetId, Quantity quantity) implements TradeFee {

        public InAsset {
            Objects.requireNonNull(assetId, "assetId must not be null");
            Objects.requireNonNull(quantity, "quantity must not be null");
        }
    }
}
