package io.matheusms.lastro.domain.service;

import io.matheusms.lastro.domain.model.EconomicIndicator;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Benchmarks as growth factors over a period, comparable with the portfolio's time-weighted return index.
 */
public final class BenchmarkIndex {

    private static final MathContext PRECISION = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private BenchmarkIndex() {
    }

    /**
     * Compounds daily rates (CDI, Selic, in % per day) published after {@code start} and up to each date.
     *
     * @return growth factor by date; 1 on {@code start}
     */
    public static NavigableMap<LocalDate, BigDecimal> compoundDailyRates(
            Collection<EconomicIndicator> dailyRates, LocalDate start) {
        var result = new TreeMap<LocalDate, BigDecimal>();
        var factor = BigDecimal.ONE;
        result.put(start, factor);
        var sorted = new TreeMap<LocalDate, BigDecimal>();
        dailyRates.forEach(rate -> sorted.put(rate.date(), rate.value()));
        for (var entry : sorted.tailMap(start, false).entrySet()) {
            factor = factor.multiply(BigDecimal.ONE.add(entry.getValue().divide(HUNDRED, PRECISION)), PRECISION);
            result.put(entry.getKey(), factor);
        }
        return result;
    }

    /**
     * Ratio of each level (e.g. Ibovespa points) to the latest level on or before {@code start}.
     *
     * @return empty when there is no level on or before {@code start}
     */
    public static Optional<NavigableMap<LocalDate, BigDecimal>> relativeToStart(
            Collection<EconomicIndicator> levels, LocalDate start) {
        var sorted = new TreeMap<LocalDate, BigDecimal>();
        levels.forEach(level -> sorted.put(level.date(), level.value()));
        var base = sorted.floorEntry(start);
        if (base == null || base.getValue().signum() == 0) {
            return Optional.empty();
        }
        var result = new TreeMap<LocalDate, BigDecimal>();
        result.put(start, BigDecimal.ONE);
        sorted.tailMap(start, false).forEach((date, level) ->
                result.put(date, level.divide(base.getValue(), PRECISION)));
        return Optional.of(result);
    }

    /** Value of a step series on a date (latest on or before), or empty before the series starts. */
    public static Optional<BigDecimal> on(NavigableMap<LocalDate, BigDecimal> series, LocalDate date) {
        return Optional.ofNullable(series.floorEntry(date)).map(Map.Entry::getValue);
    }
}
