package io.matheusms.lastro.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.matheusms.lastro.domain.model.EconomicIndicator;
import io.matheusms.lastro.domain.model.IndicatorType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BenchmarkIndexTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 5);

    @Test
    void shouldCompoundDailyRatesPublishedAfterTheStart() {
        var series = BenchmarkIndex.compoundDailyRates(List.of(
                cdi(START, "1"), cdi(START.plusDays(1), "1"), cdi(START.plusDays(2), "1")), START);

        assertThat(series.get(START)).isEqualByComparingTo("1");
        assertThat(series.get(START.plusDays(2))).isEqualByComparingTo("1.0201");
        assertThat(BenchmarkIndex.on(series, START.plusDays(10))).hasValueSatisfying(
                value -> assertThat(value).isEqualByComparingTo("1.0201"));
    }

    @Test
    void shouldRelateLevelsToTheLevelOnTheStart() {
        var series = BenchmarkIndex.relativeToStart(List.of(
                ibov(START.minusDays(1), "100000"), ibov(START.plusDays(1), "110000")), START);

        assertThat(series).hasValueSatisfying(values -> {
            assertThat(values.get(START)).isEqualByComparingTo("1");
            assertThat(values.get(START.plusDays(1))).isEqualByComparingTo("1.1");
        });
    }

    @Test
    void shouldBeEmptyWithoutALevelBeforeTheStart() {
        assertThat(BenchmarkIndex.relativeToStart(List.of(ibov(START.plusDays(1), "1")), START)).isEmpty();
    }

    private static EconomicIndicator cdi(LocalDate date, String value) {
        return new EconomicIndicator(IndicatorType.CDI, date, new BigDecimal(value));
    }

    private static EconomicIndicator ibov(LocalDate date, String value) {
        return new EconomicIndicator(IndicatorType.IBOV, date, new BigDecimal(value));
    }
}
