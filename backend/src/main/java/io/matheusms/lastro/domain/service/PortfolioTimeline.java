package io.matheusms.lastro.domain.service;

import io.matheusms.lastro.domain.model.AssetHistory;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Position;
import io.matheusms.lastro.domain.model.Trade;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Daily market value of a portfolio and its time-weighted return (pure function).
 * <p>
 * The value of each day is the quantity held at the end of the day times the latest close in BRL; an asset without
 * any quote is valued at its average cost. The time-weighted return (TWR) neutralizes contributions and
 * withdrawals, so it can be compared with CDI and Ibovespa: each day's factor is
 * {@code (value - netCashFlow) / previousValue}, with cash flows considered at the end of the day.
 */
public class PortfolioTimeline {

    private static final MathContext PRECISION = MathContext.DECIMAL64;

    private final PositionCalculator calculator = new PositionCalculator();

    /**
     * @param histories BRL histories by asset id (see {@link BrlConverter})
     * @param cashFlows BRL cash flows of the same portfolio
     * @return one point per calendar day from the first event until {@code to}; empty when there are no trades
     */
    public List<DailyValue> build(
            Map<Long, AssetHistory> histories, Collection<CashFlow> cashFlows, MarketPrices prices, LocalDate to) {
        var positionsByAsset = new HashMap<Long, NavigableMap<LocalDate, Position>>();
        histories.forEach((assetId, history) -> positionsByAsset.put(assetId, positionsOverTime(history)));
        var firstDay = positionsByAsset.values().stream()
                .filter(positions -> !positions.isEmpty())
                .map(NavigableMap::firstKey)
                .min(LocalDate::compareTo);
        if (firstDay.isEmpty() || firstDay.get().isAfter(to)) {
            return List.of();
        }
        var flowsByDay = new TreeMap<LocalDate, Money>();
        cashFlows.forEach(flow -> flowsByDay.merge(flow.date(), flow.amount(), Money::plus));

        var points = new ArrayList<DailyValue>();
        var previousValue = Money.zero(Currency.BRL);
        var invested = Money.zero(Currency.BRL);
        var index = BigDecimal.ONE;
        for (var day = firstDay.get(); !day.isAfter(to); day = day.plusDays(1)) {
            var value = valueOn(day, positionsByAsset, prices);
            var flow = flowsByDay.getOrDefault(day, Money.zero(Currency.BRL));
            if (previousValue.amount().signum() > 0) {
                var factor = value.minus(flow).amount().divide(previousValue.amount(), PRECISION);
                index = index.multiply(factor, PRECISION);
            }
            invested = invested.plus(flow);
            points.add(new DailyValue(day, value.roundedToCents(), invested.roundedToCents(), index));
            previousValue = value;
        }
        return points;
    }

    /** Position after each date with events: the step function of the asset's holdings. */
    private NavigableMap<LocalDate, Position> positionsOverTime(AssetHistory history) {
        var eventDates = new TreeSet<LocalDate>();
        history.trades().forEach(trade -> eventDates.add(trade.date()));
        history.transfers().forEach(transfer -> eventDates.add(transfer.date()));
        var firstTrade = history.trades().stream().map(Trade::date).min(LocalDate::compareTo);
        history.corporateActions().stream()
                .filter(action -> firstTrade.isPresent() && !action.recordDate().isBefore(firstTrade.get()))
                .forEach(action -> eventDates.add(action.recordDate()));

        var positions = new TreeMap<LocalDate, Position>();
        for (LocalDate date : eventDates) {
            Predicate<LocalDate> upToDate = eventDate -> !eventDate.isAfter(date);
            var partial = new AssetHistory(history.assetId(),
                    history.trades().stream().filter(trade -> upToDate.test(trade.date())).toList(),
                    history.transfers().stream().filter(transfer -> upToDate.test(transfer.date())).toList(),
                    history.corporateActions().stream().filter(action -> upToDate.test(action.recordDate())).toList());
            positions.put(date, calculator.calculate(partial));
        }
        return positions;
    }

    private static Money valueOn(
            LocalDate day, Map<Long, NavigableMap<LocalDate, Position>> positionsByAsset, MarketPrices prices) {
        var total = Money.zero(Currency.BRL);
        for (var entry : positionsByAsset.entrySet()) {
            var position = Optional.ofNullable(entry.getValue().floorEntry(day)).map(Map.Entry::getValue);
            if (position.isEmpty() || position.get().isClosed()) {
                continue;
            }
            var unitValue = prices.closeInBrlOn(entry.getKey(), day).orElse(position.get().averageCost());
            total = total.plus(unitValue.times(position.get().quantity()));
        }
        return total;
    }

    /**
     * @param marketValue value of the holdings at the end of the day, in BRL
     * @param invested cumulative net cash flows (contributions minus withdrawals) up to the day, in BRL
     * @param returnIndex cumulative time-weighted return as a growth factor (1.10 = +10% since the first day)
     */
    public record DailyValue(LocalDate date, Money marketValue, Money invested, BigDecimal returnIndex) {
    }
}
