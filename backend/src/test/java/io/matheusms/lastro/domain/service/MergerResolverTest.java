package io.matheusms.lastro.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.matheusms.lastro.domain.model.AssetHistory;
import io.matheusms.lastro.domain.model.CorporateAction;
import io.matheusms.lastro.domain.model.CorporateActionType;
import io.matheusms.lastro.domain.model.EntrySource;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quantity;
import io.matheusms.lastro.domain.model.Trade;
import io.matheusms.lastro.domain.model.TradeFee;
import io.matheusms.lastro.domain.model.TradeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MergerResolverTest {

    private static final long OLD = 1L;
    private static final long NEW = 2L;
    private static final long NEWER = 3L;
    private static final LocalDate DAY_1 = LocalDate.of(2024, 1, 10);
    private static final LocalDate DAY_2 = LocalDate.of(2024, 6, 10);
    private static final LocalDate DAY_3 = LocalDate.of(2025, 1, 10);

    private final MergerResolver resolver = new MergerResolver();
    private final PositionCalculator calculator = new PositionCalculator();

    @Test
    void shouldMoveTheWholeCostToTheSuccessor() {
        var histories = Map.of(OLD, new AssetHistory(OLD, List.of(buy(OLD, DAY_1, "70", "20.00")), List.of(),
                List.of(merger(OLD, NEW, DAY_2, "0.5"))));

        var resolved = resolver.resolve(histories);

        assertThat(calculator.calculate(resolved.get(OLD)).isClosed()).isTrue();
        var successor = calculator.calculate(resolved.get(NEW));
        assertThat(successor.quantity()).isEqualTo(Quantity.of(35));
        assertThat(successor.totalCost()).isEqualTo(Money.brl("1400.00"));
    }

    @Test
    void shouldDropTheFractionAtTheAverageCost() {
        var histories = Map.of(OLD, new AssetHistory(OLD, List.of(buy(OLD, DAY_1, "64", "10.00")), List.of(),
                List.of(merger(OLD, NEW, DAY_2, "0.8"))));

        var successor = calculator.calculate(resolver.resolve(histories).get(NEW));

        assertThat(successor.quantity()).isEqualTo(Quantity.of(51));
        assertThat(successor.averageCost()).isEqualTo(Money.brl("12.50"));
    }

    @Test
    void shouldKeepTheSuccessorsOwnHistory() {
        var histories = Map.of(
                OLD, new AssetHistory(OLD, List.of(buy(OLD, DAY_1, "10", "10.00")), List.of(),
                        List.of(merger(OLD, NEW, DAY_2, "1"))),
                NEW, new AssetHistory(NEW, List.of(buy(NEW, DAY_3, "10", "30.00")), List.of(), List.of()));

        var successor = calculator.calculate(resolver.resolve(histories).get(NEW));

        assertThat(successor.quantity()).isEqualTo(Quantity.of(20));
        assertThat(successor.averageCost()).isEqualTo(Money.brl("20.00"));
    }

    @Test
    void shouldFollowChainsOfMergers() {
        var histories = Map.of(
                OLD, new AssetHistory(OLD, List.of(buy(OLD, DAY_1, "70", "20.00")), List.of(),
                        List.of(merger(OLD, NEW, DAY_2, "0.5"))),
                NEW, new AssetHistory(NEW, List.of(), List.of(), List.of(merger(NEW, NEWER, DAY_3, "1"))));

        var resolved = resolver.resolve(histories);

        assertThat(calculator.calculate(resolved.get(NEW)).isClosed()).isTrue();
        assertThat(calculator.calculate(resolved.get(NEWER)).quantity()).isEqualTo(Quantity.of(35));
    }

    @Test
    void shouldIgnoreMergersOfClosedPositions() {
        var histories = Map.of(OLD, new AssetHistory(OLD,
                List.of(buy(OLD, DAY_1, "10", "10.00"), sell(OLD, DAY_1, "10", "12.00")), List.of(),
                List.of(merger(OLD, NEW, DAY_2, "1"))));

        assertThat(resolver.resolve(histories)).doesNotContainKey(NEW);
    }

    private static Trade buy(long assetId, LocalDate date, String quantity, String price) {
        return trade(assetId, TradeType.BUY, date, quantity, price);
    }

    private static Trade sell(long assetId, LocalDate date, String quantity, String price) {
        return trade(assetId, TradeType.SELL, date, quantity, price);
    }

    private static Trade trade(long assetId, TradeType type, LocalDate date, String quantity, String price) {
        return new Trade(null, 1L, assetId, 10L, type, date, Quantity.of(quantity), Money.brl(price),
                TradeFee.none(io.matheusms.lastro.domain.model.Currency.BRL), EntrySource.MANUAL, null);
    }

    private static CorporateAction merger(long assetId, long successorId, LocalDate date, String factor) {
        return new CorporateAction(null, assetId, CorporateActionType.MERGER, date, new BigDecimal(factor), null,
                successorId);
    }
}
