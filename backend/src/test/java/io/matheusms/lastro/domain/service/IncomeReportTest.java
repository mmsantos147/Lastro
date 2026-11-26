package io.matheusms.lastro.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Distribution;
import io.matheusms.lastro.domain.model.DistributionType;
import io.matheusms.lastro.domain.model.EconomicIndicator;
import io.matheusms.lastro.domain.model.IndicatorType;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quantity;
import io.matheusms.lastro.domain.service.IncomeReport.MonthlyIncome;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncomeReportTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);
    private static final Long FII = 1L;
    private static final Long STOCK = 2L;

    private final IncomeReport report = new IncomeReport();
    private final MarketPrices prices = new MarketDataSnapshot(List.of(),
            List.of(new EconomicIndicator(IndicatorType.USD_BRL, LocalDate.of(2026, 1, 1), new BigDecimal("5"))));

    @Test
    void shouldSumNetIncomePerMonthIncludingMonthsWithoutIncome() {
        var result = report.build(List.of(
                brl(FII, LocalDate.of(2026, 9, 15), "10.00", "0"),
                brl(FII, LocalDate.of(2026, 11, 14), "10.00", "0"),
                brl(STOCK, LocalDate.of(2026, 11, 2), "20.00", "3.00")), prices, TODAY, 3);

        assertThat(result.months()).extracting(MonthlyIncome::month, MonthlyIncome::net).containsExactly(
                tuple(YearMonth.of(2026, 9), Money.brl("10")),
                tuple(YearMonth.of(2026, 10), Money.brl("0")),
                tuple(YearMonth.of(2026, 11), Money.brl("27")));
    }

    @Test
    void shouldAverageOnlyTheMonthsSinceTheFirstIncome() {
        var result = report.build(List.of(
                brl(FII, LocalDate.of(2026, 10, 15), "10.00", "0"),
                brl(FII, LocalDate.of(2026, 11, 14), "20.00", "0")), prices, TODAY, 3);

        assertThat(result.months()).extracting(MonthlyIncome::movingAverage)
                .containsExactly(Money.brl("0"), Money.brl("10"), Money.brl("15"));
    }

    @Test
    void shouldConvertUsdIncomeAndTotalTheLastTwelveMonthsByAsset() {
        var usd = new Distribution(null, 1L, STOCK, 9L, DistributionType.DIVIDEND, LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 10), Quantity.of(1), Money.of("2", Currency.USD), Money.of("0", Currency.USD),
                Money.of("2", Currency.USD), null, null);

        var result = report.build(List.of(usd, brl(FII, LocalDate.of(2025, 10, 1), "99.00", "0"),
                brl(FII, LocalDate.of(2026, 1, 1), "5.00", "0")), prices, TODAY, 12);

        assertThat(result.lastTwelveMonths()).isEqualTo(Money.brl("15"));
        assertThat(result.lastTwelveMonthsByAsset()).containsEntry(STOCK, Money.brl("10")).containsEntry(FII, Money.brl("5"));
    }

    private static Distribution brl(Long assetId, LocalDate paymentDate, String gross, String tax) {
        var grossAmount = Money.brl(gross);
        var withheld = Money.brl(tax);
        return new Distribution(null, 1L, assetId, 9L, DistributionType.DIVIDEND, paymentDate.minusDays(5),
                paymentDate, Quantity.of(1), grossAmount, withheld, grossAmount.minus(withheld), null, null);
    }
}
