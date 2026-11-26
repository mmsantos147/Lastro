package io.matheusms.lastro.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.matheusms.lastro.application.MarketDataService.DateRange;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MarketDataServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);

    @Test
    void shouldFetchEverythingWhenNothingIsStored() {
        assertThat(MarketDataService.missingRanges(Optional.empty(), Optional.empty(), TODAY.minusDays(30), TODAY))
                .containsExactly(new DateRange(TODAY.minusDays(30), TODAY));
    }

    @Test
    void shouldFetchOnlyAfterTheLatestStoredValue() {
        assertThat(MarketDataService.missingRanges(
                Optional.of(TODAY.minusDays(30)), Optional.of(TODAY.minusDays(2)), TODAY.minusDays(30), TODAY))
                .containsExactly(new DateRange(TODAY.minusDays(1), TODAY));
    }

    @Test
    void shouldBackfillWhenAnOlderTradeWasRecorded() {
        assertThat(MarketDataService.missingRanges(
                Optional.of(TODAY.minusDays(30)), Optional.of(TODAY), TODAY.minusDays(100), TODAY))
                .containsExactly(new DateRange(TODAY.minusDays(100), TODAY.minusDays(31)));
    }
}
