package io.matheusms.lastro.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.matheusms.lastro.domain.model.AssetHistory;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Distribution;
import io.matheusms.lastro.domain.model.DistributionType;
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

class BrlConverterTest {

    private static final Long BTC = 1L;
    private static final Long BNB = 2L;
    private static final Long STOCK = 3L;
    private static final Long EXCHANGE = 10L;
    private static final LocalDate DAY_1 = LocalDate.of(2026, 1, 5);
    private static final LocalDate DAY_2 = LocalDate.of(2026, 1, 6);

    private final BrlConverter converter = new BrlConverter();
    private final PositionCalculator calculator = new PositionCalculator();
    private final MarketPrices prices = new MarketDataSnapshot(
            List.of(new Quote(BNB, DAY_1, Money.of("600", Currency.USD), QuoteSource.BINANCE)),
            List.of(new EconomicIndicator(IndicatorType.USD_BRL, DAY_1, new BigDecimal("5.00"))));

    @Test
    void shouldConvertUsdTradesWithTheRateOfTheDate() {
        var result = converter.convert(Map.of(BTC, history(BTC,
                usdTrade(BTC, TradeType.BUY, DAY_1, "0.5", "60000", new TradeFee.InCurrency(Money.of("10", Currency.USD))))),
                List.of(), prices);

        var position = calculator.calculate(result.histories().get(BTC));
        // (0.5 × 60000 + 10) × 5.00 / 0.5
        assertThat(position.averageCost()).isEqualTo(Money.brl("300100"));
        assertThat(result.cashFlows()).containsExactly(new CashFlow(DAY_1, Money.brl("150050")));
    }

    @Test
    void shouldUseTheLatestRateBeforeTheDateOnWeekends() {
        var saturday = LocalDate.of(2026, 1, 10);
        var result = converter.convert(Map.of(BTC, history(BTC,
                usdTrade(BTC, TradeType.BUY, saturday, "1", "100", TradeFee.none(Currency.USD)))), List.of(), prices);

        assertThat(calculator.calculate(result.histories().get(BTC)).averageCost()).isEqualTo(Money.brl("500"));
    }

    @Test
    void shouldAddFeePaidInAnotherAssetToCostAndDeductItFromThatAsset() {
        var histories = Map.of(
                BNB, history(BNB, usdTrade(BNB, TradeType.BUY, DAY_1, "1", "600", TradeFee.none(Currency.USD))),
                BTC, history(BTC, usdTrade(BTC, TradeType.BUY, DAY_2, "0.1", "60000",
                        new TradeFee.InAsset(BNB, Quantity.of("0.01")))));

        var result = converter.convert(histories, List.of(), prices);

        // fee: 0.01 BNB × 600 USD × 5.00 = 30 BRL; cost: 0.1 × 60000 × 5 + 30 = 30030
        assertThat(calculator.calculate(result.histories().get(BTC)).totalCost().roundedToCents())
                .isEqualTo(Money.brl("30030.00"));
        var bnb = calculator.calculate(result.histories().get(BNB));
        assertThat(bnb.quantity()).isEqualTo(Quantity.of("0.99"));
        assertThat(bnb.averageCost()).isEqualTo(Money.brl("3000"));
        // The fee paid in BNB is not new money: only the BTC price is a contribution
        assertThat(result.cashFlows()).contains(new CashFlow(DAY_2, Money.brl("30000")));
    }

    @Test
    void shouldValueFeePaidInTheTradedAssetAtTheTradePrice() {
        var result = converter.convert(Map.of(BTC, history(BTC, usdTrade(BTC, TradeType.BUY, DAY_1, "1", "100",
                new TradeFee.InAsset(BTC, Quantity.of("0.001"))))), List.of(), prices);

        var position = calculator.calculate(result.histories().get(BTC));
        assertThat(position.quantity()).isEqualTo(Quantity.of("0.999"));
        // cost 500 + fee 0.5, spread over the 1 BTC bought; the fee leaves at the average cost
        assertThat(position.averageCost()).isEqualTo(Money.brl("500.5"));
    }

    @Test
    void shouldTreatSalesAndCashDistributionsAsWithdrawals() {
        var histories = Map.of(STOCK, history(STOCK,
                brlTrade(STOCK, TradeType.BUY, DAY_1, "10", "10.00", "1.00"),
                brlTrade(STOCK, TradeType.SELL, DAY_2, "5", "12.00", "0.50")));
        var dividend = new Distribution(null, 1L, STOCK, EXCHANGE, DistributionType.DIVIDEND, DAY_1, DAY_2,
                Quantity.of(5), Money.brl("3.00"), Money.brl("0"), Money.brl("3.00"), null, null);

        var result = converter.convert(histories, List.of(dividend), prices);

        assertThat(result.cashFlows()).containsExactlyInAnyOrder(
                new CashFlow(DAY_1, Money.brl("101.00")),
                new CashFlow(DAY_2, Money.brl("-59.50")),
                new CashFlow(DAY_2, Money.brl("-3.00")));
    }

    @Test
    void shouldAddStakingRewardsAsQuantityAtTheirValue() {
        var histories = Map.of(BTC, history(BTC, usdTrade(BTC, TradeType.BUY, DAY_1, "1", "100",
                TradeFee.none(Currency.USD))));
        var reward = new Distribution(null, 1L, BTC, EXCHANGE, DistributionType.STAKING_REWARD, DAY_2, DAY_2,
                Quantity.of(1), Money.of("10", Currency.USD), Money.of("0", Currency.USD), Money.of("10", Currency.USD),
                BTC, Quantity.of("0.1"));

        var result = converter.convert(histories, List.of(reward), prices);

        var position = calculator.calculate(result.histories().get(BTC));
        assertThat(position.quantity()).isEqualTo(Quantity.of("1.1"));
        assertThat(position.totalCost().roundedToCents()).isEqualTo(Money.brl("550.00"));
        assertThat(result.cashFlows()).containsExactly(new CashFlow(DAY_1, Money.brl("500")));
    }

    @Test
    void shouldFailClearlyWhenTheExchangeRateIsMissing() {
        var before = DAY_1.minusDays(10);
        var histories = Map.of(BTC, history(BTC, usdTrade(BTC, TradeType.BUY, before, "1", "100",
                TradeFee.none(Currency.USD))));

        assertThatThrownBy(() -> converter.convert(histories, List.of(), prices))
                .isInstanceOf(MissingMarketDataException.class);
    }

    @Test
    void shouldValidateQuantitiesWithoutMarketData() {
        var histories = Map.of(BTC, history(BTC,
                usdTrade(BTC, TradeType.BUY, DAY_1, "1", "100", new TradeFee.InAsset(BTC, Quantity.of("0.1")))));

        var result = converter.convert(histories, List.of(), MarketPrices.QUANTITIES_ONLY);

        assertThat(calculator.calculate(result.histories().get(BTC)).quantity()).isEqualTo(Quantity.of("0.9"));
    }

    private static AssetHistory history(Long assetId, Trade... trades) {
        return new AssetHistory(assetId, List.of(trades), List.of(), List.of());
    }

    private static Trade usdTrade(Long assetId, TradeType type, LocalDate date, String quantity, String price,
            TradeFee fee) {
        return new Trade(null, 1L, assetId, EXCHANGE, type, date, Quantity.of(quantity),
                Money.of(price, Currency.USD), fee, EntrySource.MANUAL, null);
    }

    private static Trade brlTrade(Long assetId, TradeType type, LocalDate date, String quantity, String price,
            String fee) {
        return new Trade(null, 1L, assetId, EXCHANGE, type, date, Quantity.of(quantity), Money.brl(price),
                new TradeFee.InCurrency(Money.brl(fee)), EntrySource.MANUAL, null);
    }
}
