package io.matheusms.lastro.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.matheusms.lastro.domain.model.AssetHistory;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.EconomicIndicator;
import io.matheusms.lastro.domain.model.EntrySource;
import io.matheusms.lastro.domain.model.IndicatorType;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quantity;
import io.matheusms.lastro.domain.model.Quote;
import io.matheusms.lastro.domain.model.QuoteSource;
import io.matheusms.lastro.domain.model.Trade;
import io.matheusms.lastro.domain.model.TradeFee;
import io.matheusms.lastro.domain.model.TradeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PortfolioTimelineTest {

    private static final Long STOCK = 1L;
    private static final LocalDate DAY_1 = LocalDate.of(2026, 1, 5);
    private static final LocalDate DAY_2 = DAY_1.plusDays(1);
    private static final LocalDate DAY_3 = DAY_1.plusDays(2);

    private final PortfolioTimeline timeline = new PortfolioTimeline();

    @Test
    void shouldBeEmptyWithoutTrades() {
        assertThat(timeline.build(Map.of(), List.of(), prices(), DAY_3)).isEmpty();
    }

    @Test
    void shouldValueHoldingsAtTheLatestCloseAndCarryItOver() {
        var points = timeline.build(Map.of(STOCK, history(buy(DAY_1, "10", "10.00"))),
                List.of(new CashFlow(DAY_1, Money.brl("100"))),
                prices(quote(DAY_1, "10.00"), quote(DAY_2, "11.00")), DAY_3);

        assertThat(points).extracting(PortfolioTimeline.DailyValue::marketValue)
                .containsExactly(Money.brl("100.00"), Money.brl("110.00"), Money.brl("110.00"));
        assertThat(points.getLast().invested()).isEqualTo(Money.brl("100.00"));
        assertThat(points.getLast().returnIndex()).isEqualByComparingTo("1.1");
    }

    @Test
    void shouldNotCountContributionsAsReturn() {
        var history = history(buy(DAY_1, "10", "10.00"), buy(DAY_2, "10", "10.00"));
        var flows = List.of(new CashFlow(DAY_1, Money.brl("100")), new CashFlow(DAY_2, Money.brl("100")));

        var points = timeline.build(Map.of(STOCK, history), flows, prices(quote(DAY_1, "10.00")), DAY_2);

        assertThat(points.getLast().marketValue()).isEqualTo(Money.brl("200.00"));
        assertThat(points.getLast().returnIndex()).isEqualByComparingTo("1");
    }

    @Test
    void shouldCountWithdrawnDistributionsAsReturn() {
        var flows = List.of(new CashFlow(DAY_1, Money.brl("100")), new CashFlow(DAY_2, Money.brl("-5")));

        var points = timeline.build(Map.of(STOCK, history(buy(DAY_1, "10", "10.00"))), flows,
                prices(quote(DAY_1, "10.00")), DAY_2);

        assertThat(points.getLast().returnIndex()).isEqualByComparingTo("1.05");
    }

    @Test
    void shouldValueAssetsWithoutQuotesAtAverageCost() {
        var points = timeline.build(Map.of(STOCK, history(buy(DAY_1, "2", "50.00"))), List.of(), prices(), DAY_1);

        assertThat(points.getFirst().marketValue()).isEqualTo(Money.brl("100.00"));
    }

    @Test
    void shouldConvertUsdQuotesWithTheRateOfTheDay() {
        var prices = new MarketDataSnapshot(
                List.of(new Quote(STOCK, DAY_1, Money.of("2", Currency.USD), QuoteSource.BINANCE)),
                List.of(new EconomicIndicator(IndicatorType.USD_BRL, DAY_1, new BigDecimal("5")),
                        new EconomicIndicator(IndicatorType.USD_BRL, DAY_2, new BigDecimal("6"))));

        var points = timeline.build(Map.of(STOCK, history(buy(DAY_1, "10", "10.00"))), List.of(), prices, DAY_2);

        assertThat(points).extracting(PortfolioTimeline.DailyValue::marketValue)
                .containsExactly(Money.brl("100.00"), Money.brl("120.00"));
    }

    private static MarketDataSnapshot prices(Quote... quotes) {
        return new MarketDataSnapshot(List.of(quotes), List.of());
    }

    private static Quote quote(LocalDate date, String close) {
        return new Quote(STOCK, date, Money.brl(close), QuoteSource.BRAPI);
    }

    private static AssetHistory history(Trade... trades) {
        return new AssetHistory(STOCK, List.of(trades), List.of(), List.of());
    }

    private static Trade buy(LocalDate date, String quantity, String price) {
        return new Trade(null, 1L, STOCK, 9L, TradeType.BUY, date, Quantity.of(quantity), Money.brl(price),
                TradeFee.none(Currency.BRL), EntrySource.MANUAL, null);
    }
}
