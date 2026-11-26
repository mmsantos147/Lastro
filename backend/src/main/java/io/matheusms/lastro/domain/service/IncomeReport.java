package io.matheusms.lastro.domain.service;

import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Distribution;
import io.matheusms.lastro.domain.model.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Passive income from distributions, in BRL (pure function): totals per month, the trailing 12-month average and
 * the income of the last 12 months per asset. Amounts are net of withheld tax and converted at the payment date.
 */
public class IncomeReport {

    private static final int AVERAGE_WINDOW_MONTHS = 12;

    /**
     * @param months how many months to return, ending with the month of {@code today} (months without income are
     *     included with zero)
     * @throws MissingMarketDataException if a USD distribution has no rate for its payment date
     */
    public Result build(Collection<Distribution> distributions, MarketPrices prices, LocalDate today, int months) {
        var byMonth = new TreeMap<YearMonth, Money>();
        var last12ByAsset = new HashMap<Long, Money>();
        var last12Start = today.minusMonths(AVERAGE_WINDOW_MONTHS);
        for (Distribution distribution : distributions) {
            if (distribution.paymentDate().isAfter(today)) {
                continue;
            }
            var net = prices.inBrl(distribution.netAmount(), distribution.paymentDate())
                    .orElseThrow(() -> new MissingMarketDataException(
                            "No USD/BRL rate on or before " + distribution.paymentDate()));
            byMonth.merge(YearMonth.from(distribution.paymentDate()), net, Money::plus);
            if (distribution.paymentDate().isAfter(last12Start)) {
                last12ByAsset.merge(distribution.assetId(), net, Money::plus);
            }
        }

        var currentMonth = YearMonth.from(today);
        var firstIncomeMonth = byMonth.isEmpty() ? currentMonth : byMonth.firstKey();
        var points = new ArrayList<MonthlyIncome>();
        for (var month = currentMonth.minusMonths(months - 1L); !month.isAfter(currentMonth); month = month.plusMonths(1)) {
            points.add(new MonthlyIncome(month, byMonth.getOrDefault(month, zero()),
                    trailingAverage(byMonth, month, firstIncomeMonth)));
        }
        var last12 = last12ByAsset.values().stream().reduce(zero(), Money::plus);
        return new Result(points, last12, Map.copyOf(last12ByAsset));
    }

    /** Average of the 12 months ending at {@code month}, or of the months since the first income when fewer. */
    private static Money trailingAverage(Map<YearMonth, Money> byMonth, YearMonth month, YearMonth firstIncomeMonth) {
        if (month.isBefore(firstIncomeMonth)) {
            return zero();
        }
        var total = zero();
        int count = 0;
        for (var current = month; count < AVERAGE_WINDOW_MONTHS && !current.isBefore(firstIncomeMonth);
                current = current.minusMonths(1)) {
            total = total.plus(byMonth.getOrDefault(current, zero()));
            count++;
        }
        return total.dividedBy(BigDecimal.valueOf(count));
    }

    private static Money zero() {
        return Money.zero(Currency.BRL);
    }

    /** @param movingAverage trailing 12-month average (fewer months at the beginning of the history) */
    public record MonthlyIncome(YearMonth month, Money net, Money movingAverage) {
    }

    /**
     * @param lastTwelveMonths net income of the last 12 months
     * @param lastTwelveMonthsByAsset the same, by asset id
     */
    public record Result(List<MonthlyIncome> months, Money lastTwelveMonths, Map<Long, Money> lastTwelveMonthsByAsset) {
    }
}
