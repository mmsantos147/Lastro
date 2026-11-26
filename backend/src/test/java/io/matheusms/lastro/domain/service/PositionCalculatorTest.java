package io.matheusms.lastro.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.matheusms.lastro.domain.model.AssetHistory;
import io.matheusms.lastro.domain.model.CorporateAction;
import io.matheusms.lastro.domain.model.CorporateActionType;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.EntrySource;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Position;
import io.matheusms.lastro.domain.model.Quantity;
import io.matheusms.lastro.domain.model.Trade;
import io.matheusms.lastro.domain.model.TradeFee;
import io.matheusms.lastro.domain.model.TradeType;
import io.matheusms.lastro.domain.model.Transfer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PositionCalculatorTest {

    private static final Long ASSET_ID = 1L;
    private static final Long BROKER_ID = 10L;
    private static final Long WALLET_ID = 20L;
    private static final LocalDate DAY_1 = LocalDate.of(2026, 1, 5);
    private static final LocalDate DAY_2 = LocalDate.of(2026, 1, 6);
    private static final LocalDate DAY_3 = LocalDate.of(2026, 1, 7);

    private final PositionCalculator calculator = new PositionCalculator();

    @Test
    void shouldReturnEmptyPositionWhenThereAreNoTrades() {
        var position = calculate(List.of(), List.of(), List.of());

        assertThat(position).isEqualTo(Position.empty(ASSET_ID));
    }

    @Nested
    class Buying {

        @Test
        void shouldIncludeFeesInAverageCost() {
            var position = calculateTrades(buy(DAY_1, "100", "10.00", "5.00"));

            assertPosition(position, "100", "10.05");
            assertThat(position.totalCost()).isEqualTo(Money.brl("1005.00"));
        }

        @Test
        void shouldComputeWeightedAverageCostAcrossBuys() {
            var position = calculateTrades(
                    buy(DAY_1, "100", "10.00", "0"),
                    buy(DAY_2, "50", "13.00", "0"));

            assertPosition(position, "150", "11.00");
        }

        @Test
        void shouldRoundAverageCostToEightDecimalPlacesHalfEven() {
            var position = calculateTrades(buy(DAY_1, "3", "10.00", "1.00"));

            assertPosition(position, "3", "10.33333333");
        }

        @Test
        void shouldSupportFractionalQuantities() {
            var position = calculateTrades(
                    buy(DAY_1, "0.53", "14000.00", "0"),
                    sell(DAY_2, "0.21", "14500.00", "0"));

            assertPosition(position, "0.32", "14000.00");
        }
    }

    @Nested
    class Selling {

        @Test
        void shouldKeepAverageCostWhenSelling() {
            var position = calculateTrades(
                    buy(DAY_1, "100", "10.00", "5.00"),
                    sell(DAY_2, "40", "20.00", "3.00"));

            assertPosition(position, "60", "10.05");
            assertThat(position.totalCost()).isEqualTo(Money.brl("603.00"));
        }

        @Test
        void shouldResetAverageCostWhenPositionIsClosed() {
            var position = calculateTrades(
                    buy(DAY_1, "10", "10.00", "0"),
                    sell(DAY_2, "10", "12.00", "0"));

            assertThat(position).isEqualTo(Position.empty(ASSET_ID));
        }

        @Test
        void shouldUseOnlyNewCostWhenBuyingAfterClosingPosition() {
            var position = calculateTrades(
                    buy(DAY_1, "10", "10.00", "0"),
                    sell(DAY_2, "10", "12.00", "0"),
                    buy(DAY_3, "5", "20.00", "0"));

            assertPosition(position, "5", "20.00");
        }

        @Test
        void shouldRejectSellingMoreThanHeld() {
            assertThatThrownBy(() -> calculateTrades(
                    buy(DAY_1, "10", "10.00", "0"),
                    sell(DAY_2, "11", "12.00", "0")))
                    .isInstanceOf(InsufficientQuantityException.class);
        }

        @Test
        void shouldRejectSellingBeforeBuying() {
            assertThatThrownBy(() -> calculateTrades(
                    sell(DAY_1, "10", "12.00", "0"),
                    buy(DAY_2, "10", "10.00", "0")))
                    .isInstanceOf(InsufficientQuantityException.class);
        }
    }

    @Nested
    class Transfers {

        @Test
        void shouldKeepPositionWhenTransferringWithoutFee() {
            var position = calculate(
                    List.of(buy(DAY_1, "0.5", "300000.00", "0")),
                    List.of(transfer(DAY_2, "0.5", "0")),
                    List.of());

            assertPosition(position, "0.5", "300000.00");
        }

        @Test
        void shouldReduceQuantityByTransferFeeKeepingAverageCost() {
            var position = calculate(
                    List.of(buy(DAY_1, "0.5", "300000.00", "0")),
                    List.of(transfer(DAY_2, "0.5", "0.0002")),
                    List.of());

            // The fee leaves at the average cost, like a sale without proceeds
            assertPosition(position, "0.4998", "300000.00");
            assertThat(position.totalCost()).isEqualTo(Money.brl("149940.00"));
        }

        @Test
        void shouldRejectTransferringMoreThanHeld() {
            var trades = List.of(buy(DAY_1, "0.5", "300000.00", "0"));
            var transfers = List.of(transfer(DAY_2, "0.6", "0"));

            assertThatThrownBy(() -> calculate(trades, transfers, List.of()))
                    .isInstanceOf(InsufficientQuantityException.class);
        }

        @Test
        void shouldApplyTradesBeforeTransfersOnTheSameDate() {
            var position = calculate(
                    List.of(buy(DAY_1, "0.5", "300000.00", "0")),
                    List.of(transfer(DAY_1, "0.5", "0.0002")),
                    List.of());

            assertPosition(position, "0.4998", "300000.00");
        }
    }

    @Nested
    class CorporateActions {

        @Test
        void shouldMultiplyQuantityAndDivideAverageCostOnStockSplit() {
            var position = calculate(
                    List.of(buy(DAY_1, "100", "10.00", "0")),
                    List.of(),
                    List.of(action(CorporateActionType.STOCK_SPLIT, DAY_2, "2")));

            assertPosition(position, "200", "5.00");
            assertThat(position.totalCost()).isEqualTo(Money.brl("1000.00"));
        }

        @Test
        void shouldDivideQuantityAndMultiplyAverageCostOnReverseSplit() {
            var position = calculate(
                    List.of(buy(DAY_1, "100", "1.00", "0")),
                    List.of(),
                    List.of(action(CorporateActionType.REVERSE_SPLIT, DAY_2, "10")));

            assertPosition(position, "10", "10.00");
        }

        @Test
        void shouldDropFractionalSharesAtAverageCostOnReverseSplit() {
            var position = calculate(
                    List.of(buy(DAY_1, "105", "1.00", "0")),
                    List.of(),
                    List.of(action(CorporateActionType.REVERSE_SPLIT, DAY_2, "10")));

            // 105 / 10 = 10.5 shares: the 0.5 fraction is auctioned and leaves at the average cost
            assertPosition(position, "10", "10.00");
            assertThat(position.totalCost()).isEqualTo(Money.brl("100.00"));
        }

        @Test
        void shouldAddBonusSharesAtAssignedCost() {
            var position = calculate(
                    List.of(buy(DAY_1, "100", "10.00", "0")),
                    List.of(),
                    List.of(bonus(DAY_2, "0.1", "5.00")));

            // (1000.00 + 10 × 5.00) / 110
            assertPosition(position, "110", "9.54545455");
            assertThat(position.totalCost().roundedToCents()).isEqualTo(Money.brl("1050.00"));
        }

        @Test
        void shouldDropFractionalBonusShares() {
            var position = calculate(
                    List.of(buy(DAY_1, "15", "10.00", "0")),
                    List.of(),
                    List.of(bonus(DAY_2, "0.1", "4.00")));

            // 15 × 0.1 = 1.5 bonus shares: only 1 is received, at the assigned cost
            assertPosition(position, "16", "9.625");
        }

        @Test
        void shouldIgnoreCorporateActionsWithoutPosition() {
            var position = calculate(
                    List.of(buy(DAY_2, "10", "10.00", "0")),
                    List.of(),
                    List.of(action(CorporateActionType.STOCK_SPLIT, DAY_1, "2")));

            assertPosition(position, "10", "10.00");
        }

        @Test
        void shouldCloseThePositionOnMerger() {
            var trades = List.of(buy(DAY_1, "10", "10.00", "0"));
            var actions = List.of(new CorporateAction(null, ASSET_ID, CorporateActionType.MERGER, DAY_2,
                    BigDecimal.ONE, null, 99L));

            var position = calculate(trades, List.of(), actions);

            assertThat(position.isClosed()).isTrue();
        }

        @Test
        void shouldRejectUnsupportedCorporateActionTypes() {
            var trades = List.of(buy(DAY_1, "10", "10.00", "0"));
            var actions = List.of(action(CorporateActionType.AMORTIZATION, DAY_2, "1"));

            assertThatThrownBy(() -> calculate(trades, List.of(), actions))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class Ordering {

        @Test
        void shouldApplyTradesOnRecordDateBeforeCorporateAction() {
            var position = calculate(
                    List.of(
                            buy(DAY_1, "100", "10.00", "0"),
                            buy(DAY_2, "100", "10.00", "0"),
                            buy(DAY_3, "100", "8.00", "0")),
                    List.of(),
                    List.of(action(CorporateActionType.STOCK_SPLIT, DAY_2, "2")));

            // 200 shares on the record date become 400 at 5.00; then 100 more at 8.00
            assertPosition(position, "500", "5.60");
        }

        @Test
        void shouldProcessEventsChronologicallyRegardlessOfInputOrder() {
            var position = calculate(
                    List.of(
                            sell(DAY_3, "50", "12.00", "0"),
                            buy(DAY_1, "100", "10.00", "0")),
                    List.of(),
                    List.of(action(CorporateActionType.STOCK_SPLIT, DAY_2, "2")));

            assertPosition(position, "150", "5.00");
        }

        @Test
        void shouldKeepInputOrderForTradesOnTheSameDate() {
            var position = calculateTrades(
                    buy(DAY_1, "10", "10.00", "0"),
                    sell(DAY_1, "10", "12.00", "0"),
                    buy(DAY_1, "10", "20.00", "0"));

            assertPosition(position, "10", "20.00");
        }
    }

    @Nested
    class Validation {

        @Test
        void shouldRejectTradesFromAnotherAsset() {
            var trade = new Trade(null, 1L, 2L, BROKER_ID, TradeType.BUY, DAY_1, Quantity.of(10),
                    Money.brl("10.00"), TradeFee.none(Currency.BRL), EntrySource.MANUAL, null);

            assertThatThrownBy(() -> calculateTrades(trade)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectTransfersFromAnotherAsset() {
            var trades = List.of(buy(DAY_1, "10", "10.00", "0"));
            var transfers = List.of(new Transfer(null, 1L, 2L, BROKER_ID, WALLET_ID, DAY_2,
                    Quantity.of(1), Quantity.ZERO, EntrySource.MANUAL, null));

            assertThatThrownBy(() -> calculate(trades, transfers, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectCorporateActionsFromAnotherAsset() {
            var trades = List.of(buy(DAY_1, "10", "10.00", "0"));
            var actions = List.of(new CorporateAction(
                    null, 2L, CorporateActionType.STOCK_SPLIT, DAY_2, new BigDecimal("2"), null));

            assertThatThrownBy(() -> calculate(trades, List.of(), actions))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectTradesNotValuedInBrl() {
            var trade = new Trade(null, 1L, ASSET_ID, BROKER_ID, TradeType.BUY, DAY_1, Quantity.of("0.01"),
                    Money.of("65000.00", Currency.USD), TradeFee.none(Currency.USD), EntrySource.MANUAL, null);

            assertThatThrownBy(() -> calculateTrades(trade)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectFeesPaidInAnotherAsset() {
            var trade = new Trade(null, 1L, ASSET_ID, BROKER_ID, TradeType.BUY, DAY_1, Quantity.of("0.01"),
                    Money.brl("350000.00"), new TradeFee.InAsset(99L, Quantity.of("0.0015")),
                    EntrySource.MANUAL, null);

            assertThatThrownBy(() -> calculateTrades(trade)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private Position calculate(List<Trade> trades, List<Transfer> transfers, List<CorporateAction> actions) {
        return calculator.calculate(new AssetHistory(ASSET_ID, trades, transfers, actions));
    }

    private Position calculateTrades(Trade... trades) {
        return calculate(List.of(trades), List.of(), List.of());
    }

    private static void assertPosition(Position position, String quantity, String averageCost) {
        assertThat(position.assetId()).isEqualTo(ASSET_ID);
        assertThat(position.quantity()).isEqualTo(Quantity.of(quantity));
        assertThat(position.averageCost()).isEqualTo(Money.brl(averageCost));
    }

    private static Trade buy(LocalDate date, String quantity, String unitPrice, String fee) {
        return trade(TradeType.BUY, date, quantity, unitPrice, fee);
    }

    private static Trade sell(LocalDate date, String quantity, String unitPrice, String fee) {
        return trade(TradeType.SELL, date, quantity, unitPrice, fee);
    }

    private static Trade trade(TradeType type, LocalDate date, String quantity, String unitPrice, String fee) {
        return new Trade(null, 1L, ASSET_ID, BROKER_ID, type, date, Quantity.of(quantity),
                Money.brl(unitPrice), new TradeFee.InCurrency(Money.brl(fee)), EntrySource.MANUAL, null);
    }

    private static Transfer transfer(LocalDate date, String quantity, String fee) {
        return new Transfer(null, 1L, ASSET_ID, BROKER_ID, WALLET_ID, date,
                Quantity.of(quantity), Quantity.of(fee), EntrySource.MANUAL, null);
    }

    private static CorporateAction action(CorporateActionType type, LocalDate recordDate, String factor) {
        return new CorporateAction(null, ASSET_ID, type, recordDate, new BigDecimal(factor), null);
    }

    private static CorporateAction bonus(LocalDate recordDate, String factor, String assignedCost) {
        return new CorporateAction(null, ASSET_ID, CorporateActionType.BONUS_SHARES, recordDate,
                new BigDecimal(factor), Money.brl(assignedCost));
    }
}
