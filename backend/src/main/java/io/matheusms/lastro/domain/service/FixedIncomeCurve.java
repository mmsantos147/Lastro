package io.matheusms.lastro.domain.service;

import io.matheusms.lastro.domain.model.EconomicIndicator;
import io.matheusms.lastro.domain.model.FixedIncomeIndexer;
import io.matheusms.lastro.domain.model.FixedIncomeTerms;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Value of a fixed income security on its curve ("marcação na curva"), before income tax (pure function).
 * <p>
 * The price paid is accrued once per business day. The business days are the dates of the daily series given (CDI or
 * Selic, published only on business days), and the rate of a day accrues from that day to the next, so the value on a
 * business day D is {@code price × Π factor(d)} over the business days {@code start <= d < D}. Daily factors:
 * <ul>
 *   <li>CDI/Selic: {@code 1 + rate(d) × percentage}, times the spread's daily factor;</li>
 *   <li>prefixed: {@code (1 + rate)^(1/252)}, the B3 convention of 252 business days a year;</li>
 *   <li>IPCA: the real rate's daily factor times the month's IPCA spread over its weekdays
 *       ({@code (1 + ipca)^(1/weekdays)}). Before a month's IPCA is published, the latest one is used as projection;
 *       holidays are not taken out of the weekday count. Both are approximations, good enough for monitoring.</li>
 * </ul>
 * Fractional powers are computed in {@code double} and converted back: the error (~1e-16 per day) is far below the
 * 8 decimal places stored.
 */
public class FixedIncomeCurve {

    private static final MathContext PRECISION = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final double BUSINESS_DAYS_PER_YEAR = 252;

    /**
     * @param start date of the first purchase
     * @param startPrice unit price paid, in BRL
     * @param dailySeries CDI (for CDI, IPCA and prefixed securities) or Selic (for Selic ones), in % per day: the
     *     business-day calendar and, for CDI/Selic securities, the rates
     * @param monthlyIpca IPCA in % per month, dated on the first day of the month; only used by IPCA securities
     * @param to last date to value (inclusive); the maturity date stops the curve earlier
     * @return unit value on each business day from {@code start} up to {@code to} or maturity; a day's own rate is
     *     not in its value yet (it accrues overnight)
     */
    public NavigableMap<LocalDate, BigDecimal> unitValues(
            FixedIncomeTerms terms, LocalDate start, BigDecimal startPrice, Collection<EconomicIndicator> dailySeries,
            Collection<EconomicIndicator> monthlyIpca, LocalDate to) {
        var end = terms.maturityDate() != null && terms.maturityDate().isBefore(to) ? terms.maturityDate() : to;
        var days = new TreeMap<LocalDate, BigDecimal>();
        dailySeries.forEach(rate -> days.put(rate.date(), rate.value()));
        var ipca = new TreeMap<LocalDate, BigDecimal>();
        monthlyIpca.forEach(month -> ipca.put(month.date(), month.value()));

        var values = new TreeMap<LocalDate, BigDecimal>();
        var value = startPrice;
        for (var day : days.subMap(start, true, end, true).entrySet()) {
            values.put(day.getKey(), value);
            value = value.multiply(dailyFactor(terms, day.getKey(), day.getValue(), ipca), PRECISION);
        }
        return values;
    }

    private static BigDecimal dailyFactor(
            FixedIncomeTerms terms, LocalDate day, BigDecimal dailyRate, NavigableMap<LocalDate, BigDecimal> ipca) {
        var rateFactor = annualToDaily(terms.rate());
        return switch (terms.indexer()) {
            case CDI, SELIC -> BigDecimal.ONE
                    .add(dailyRate.divide(HUNDRED, PRECISION).multiply(terms.indexerPercentage().divide(HUNDRED,
                            PRECISION), PRECISION))
                    .multiply(rateFactor, PRECISION);
            case PREFIXED -> rateFactor;
            case IPCA -> rateFactor.multiply(inflationFactor(day, ipca), PRECISION);
        };
    }

    /** {@code (1 + rate/100)^(1/252)}. */
    private static BigDecimal annualToDaily(BigDecimal annualPercent) {
        if (annualPercent.signum() == 0) {
            return BigDecimal.ONE;
        }
        double annual = 1 + annualPercent.doubleValue() / 100;
        return BigDecimal.valueOf(Math.pow(annual, 1 / BUSINESS_DAYS_PER_YEAR));
    }

    /** The month's IPCA (or the latest published, as projection) spread over the month's weekdays. */
    private static BigDecimal inflationFactor(LocalDate day, NavigableMap<LocalDate, BigDecimal> ipca) {
        var month = YearMonth.from(day);
        var published = ipca.floorEntry(month.atDay(1));
        if (published == null) {
            return BigDecimal.ONE;
        }
        double monthly = 1 + published.getValue().doubleValue() / 100;
        return BigDecimal.valueOf(Math.pow(monthly, 1.0 / weekdays(month)));
    }

    private static int weekdays(YearMonth month) {
        int count = 0;
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            var weekday = month.atDay(day).getDayOfWeek();
            if (weekday != DayOfWeek.SATURDAY && weekday != DayOfWeek.SUNDAY) {
                count++;
            }
        }
        return count;
    }
}
