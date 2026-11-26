package io.matheusms.lastro.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.matheusms.lastro.domain.model.EconomicIndicator;
import io.matheusms.lastro.domain.model.FixedIncomeIndexer;
import io.matheusms.lastro.domain.model.FixedIncomeTerms;
import io.matheusms.lastro.domain.model.IndicatorType;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FixedIncomeCurveTest {

    private static final BigDecimal PRICE = new BigDecimal("0.01");
    private static final LocalDate MON = LocalDate.of(2026, 3, 2);

    private final FixedIncomeCurve curve = new FixedIncomeCurve();

    @Test
    void shouldAccrueTheCdiOfEachBusinessDayOvernight() {
        var cdi = daily(MON, 3, "0.05");

        var values = curve.unitValues(cdi(100), MON, PRICE, cdi, List.of(), MON.plusDays(10));

        assertThat(values).containsOnlyKeys(MON, MON.plusDays(1), MON.plusDays(2));
        assertThat(values.get(MON)).isEqualByComparingTo("0.01");
        assertThat(values.get(MON.plusDays(1))).isEqualByComparingTo("0.010005");
        assertThat(values.get(MON.plusDays(2))).isEqualByComparingTo("0.0100100025");
    }

    @Test
    void shouldApplyThePercentageOfTheCdi() {
        var values = curve.unitValues(cdi(110), MON, PRICE, daily(MON, 2, "0.05"), List.of(), MON.plusDays(10));

        assertThat(values.get(MON.plusDays(1))).isEqualByComparingTo("0.0100055");
    }

    @Test
    void shouldIgnoreRatesBeforeThePurchase() {
        var values = curve.unitValues(cdi(100), MON.plusDays(1), PRICE, daily(MON, 3, "0.05"), List.of(),
                MON.plusDays(10));

        assertThat(values.firstKey()).isEqualTo(MON.plusDays(1));
        assertThat(values.get(MON.plusDays(2))).isEqualByComparingTo("0.010005");
    }

    @Test
    void shouldCompoundAFixedRateOver252BusinessDays() {
        var terms = new FixedIncomeTerms(1L, null, FixedIncomeIndexer.PREFIXED, null, new BigDecimal("12"), null,
                false);
        var calendar = daily(MON, 253, "0.05");

        var values = curve.unitValues(terms, MON, PRICE, calendar, List.of(), LocalDate.of(2030, 1, 1));

        assertThat(values.lastEntry().getValue().doubleValue()).isCloseTo(0.0112, within(1e-9));
    }

    @Test
    void shouldStopAtMaturity() {
        var terms = new FixedIncomeTerms(1L, "Banco", FixedIncomeIndexer.CDI, new BigDecimal("100"), BigDecimal.ZERO,
                MON.plusDays(1), false);

        var values = curve.unitValues(terms, MON, PRICE, daily(MON, 5, "0.05"), List.of(), MON.plusDays(30));

        assertThat(values.lastKey()).isEqualTo(MON.plusDays(1));
    }

    @Test
    void shouldSpreadTheMonthsInflationOverItsWeekdays() {
        var terms = new FixedIncomeTerms(1L, null, FixedIncomeIndexer.IPCA, null, BigDecimal.ZERO, null, false);
        var january = LocalDate.of(2026, 1, 1);
        var calendar = daily(january, 23, "0.05"); // 22 weekdays in January 2026, then Feb 2
        var ipca = List.of(new EconomicIndicator(IndicatorType.IPCA, january, new BigDecimal("1")));

        var values = curve.unitValues(terms, january, PRICE, calendar, ipca, LocalDate.of(2026, 3, 1));

        assertThat(values.lastKey()).isEqualTo(LocalDate.of(2026, 2, 2));
        assertThat(values.lastEntry().getValue().doubleValue()).isCloseTo(0.0101, within(1e-12));
    }

    private static FixedIncomeTerms cdi(int percentage) {
        return new FixedIncomeTerms(1L, null, FixedIncomeIndexer.CDI, BigDecimal.valueOf(percentage),
                BigDecimal.ZERO, null, true);
    }

    /** {@code count} consecutive weekdays from {@code first} (inclusive), each with the same daily rate. */
    private static List<EconomicIndicator> daily(LocalDate first, int count, String rate) {
        var values = new ArrayList<EconomicIndicator>();
        for (var day = first; values.size() < count; day = day.plusDays(1)) {
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                values.add(new EconomicIndicator(IndicatorType.CDI, day, new BigDecimal(rate)));
            }
        }
        return values;
    }
}
