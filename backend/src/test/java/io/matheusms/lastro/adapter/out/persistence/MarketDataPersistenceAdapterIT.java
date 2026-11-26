package io.matheusms.lastro.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.EconomicIndicator;
import io.matheusms.lastro.domain.model.IndicatorType;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quote;
import io.matheusms.lastro.domain.model.QuoteSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@PersistenceAdapterTest
class MarketDataPersistenceAdapterIT {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 1, 5);
    private static final LocalDate DAY_2 = LocalDate.of(2026, 1, 6);

    @Autowired
    private QuotePersistenceAdapter quotes;
    @Autowired
    private EconomicIndicatorPersistenceAdapter indicators;
    @Autowired
    private AssetPersistenceAdapter assets;

    @Test
    void shouldUpsertQuotesByAssetDateAndCurrency() {
        var assetId = assets.save(TestData.crypto("ETH")).id();
        quotes.upsert(List.of(
                new Quote(assetId, DAY_1, Money.of("3000.12345678", Currency.USD), QuoteSource.BINANCE),
                new Quote(assetId, DAY_2, Money.of("3100", Currency.USD), QuoteSource.BINANCE)));
        quotes.upsert(List.of(new Quote(assetId, DAY_2, Money.of("3200", Currency.USD), QuoteSource.MANUAL)));

        assertThat(quotes.findByAsset(assetId)).containsExactly(
                new Quote(assetId, DAY_1, Money.of("3000.12345678", Currency.USD), QuoteSource.BINANCE),
                new Quote(assetId, DAY_2, Money.of("3200", Currency.USD), QuoteSource.MANUAL));
        assertThat(quotes.findLatestDate(assetId)).contains(DAY_2);
        assertThat(quotes.findByAssetsUpTo(List.of(assetId), DAY_1)).hasSize(1);
    }

    @Test
    void shouldUpsertIndicatorsByTypeAndDate() {
        var day = LocalDate.of(1999, 3, 1); // far from any other test data
        indicators.upsert(List.of(new EconomicIndicator(IndicatorType.CDI, day, new BigDecimal("0.050788"))));
        indicators.upsert(List.of(new EconomicIndicator(IndicatorType.CDI, day, new BigDecimal("0.051"))));

        assertThat(indicators.findByTypeBetween(IndicatorType.CDI, day, day))
                .extracting(EconomicIndicator::value)
                .singleElement()
                .satisfies(value -> assertThat(value).isEqualByComparingTo("0.051"));
    }
}
