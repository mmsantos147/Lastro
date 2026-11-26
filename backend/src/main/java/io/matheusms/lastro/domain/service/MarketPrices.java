package io.matheusms.lastro.domain.service;

import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** Market data as of a date: the latest value published on or before it (weekends and holidays carry over). */
public interface MarketPrices {

    /**
     * Prices for checks that only care about quantities (e.g. validating a new entry before market data exists):
     * every asset costs zero and a dollar costs one real.
     */
    MarketPrices QUANTITIES_ONLY = new MarketPrices() {
        @Override
        public Optional<Money> closeOn(Long assetId, LocalDate date) {
            return Optional.of(Money.zero(Currency.BRL));
        }

        @Override
        public Optional<BigDecimal> usdBrlOn(LocalDate date) {
            return Optional.of(BigDecimal.ONE);
        }
    };

    /** Latest close of the asset on or before the date, in the asset's quote currency. */
    Optional<Money> closeOn(Long assetId, LocalDate date);

    /** Latest BRL per USD rate on or before the date. */
    Optional<BigDecimal> usdBrlOn(LocalDate date);

    /** Converts to BRL with the rate of the date. */
    default Optional<Money> inBrl(Money money, LocalDate date) {
        if (money.isIn(Currency.BRL)) {
            return Optional.of(money);
        }
        return usdBrlOn(date).map(rate -> new Money(money.amount().multiply(rate), Currency.BRL));
    }

    /** Latest close of the asset on or before the date, converted to BRL. */
    default Optional<Money> closeInBrlOn(Long assetId, LocalDate date) {
        return closeOn(assetId, date).flatMap(close -> inBrl(close, date));
    }
}
