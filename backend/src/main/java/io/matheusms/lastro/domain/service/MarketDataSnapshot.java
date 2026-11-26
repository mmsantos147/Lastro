package io.matheusms.lastro.domain.service;

import io.matheusms.lastro.domain.model.EconomicIndicator;
import io.matheusms.lastro.domain.model.IndicatorType;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quote;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/** {@link MarketPrices} backed by quotes and USD/BRL rates loaded in memory. */
public final class MarketDataSnapshot implements MarketPrices {

    private final Map<Long, NavigableMap<LocalDate, Money>> closesByAsset = new HashMap<>();
    private final NavigableMap<LocalDate, BigDecimal> usdBrl = new TreeMap<>();

    public MarketDataSnapshot(Collection<Quote> quotes, Collection<EconomicIndicator> usdBrlRates) {
        for (Quote quote : quotes) {
            closesByAsset.computeIfAbsent(quote.assetId(), id -> new TreeMap<>()).put(quote.date(), quote.closePrice());
        }
        for (EconomicIndicator rate : usdBrlRates) {
            if (rate.type() != IndicatorType.USD_BRL) {
                throw new IllegalArgumentException("Expected USD_BRL rates, got " + rate.type());
            }
            usdBrl.put(rate.date(), rate.value());
        }
    }

    @Override
    public Optional<Money> closeOn(Long assetId, LocalDate date) {
        var closes = closesByAsset.get(assetId);
        return closes == null ? Optional.empty() : Optional.ofNullable(closes.floorEntry(date)).map(Map.Entry::getValue);
    }

    @Override
    public Optional<BigDecimal> usdBrlOn(LocalDate date) {
        return Optional.ofNullable(usdBrl.floorEntry(date)).map(Map.Entry::getValue);
    }

    /** Date of the latest close of the asset on or before the date. */
    public Optional<LocalDate> closeDateOn(Long assetId, LocalDate date) {
        var closes = closesByAsset.get(assetId);
        return closes == null ? Optional.empty() : Optional.ofNullable(closes.floorKey(date));
    }
}
