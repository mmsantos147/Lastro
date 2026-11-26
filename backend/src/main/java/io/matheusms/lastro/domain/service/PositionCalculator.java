package io.matheusms.lastro.domain.service;

import io.matheusms.lastro.domain.model.AssetHistory;
import io.matheusms.lastro.domain.model.CorporateAction;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Position;
import io.matheusms.lastro.domain.model.Quantity;
import io.matheusms.lastro.domain.model.Trade;
import io.matheusms.lastro.domain.model.TradeFee;
import io.matheusms.lastro.domain.model.Transfer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Derives the consolidated position of a single asset from its history (pure function). Works in BRL only:
 * trades in other currencies or with fees paid in another asset must be converted beforehand.
 * <p>
 * Rules (Receita Federal):
 * <ul>
 *   <li>Buying: purchase fees are part of the acquisition cost and enter the average cost.</li>
 *   <li>Selling: reduces quantity without changing the average cost; sale fees are ignored. Closing the
 *       position resets the average cost.</li>
 *   <li>Transfer between the user's own institutions: does not change the position, except for the fee
 *       (paid in the asset itself), which leaves at the average cost.</li>
 *   <li>Stock split / reverse split: change quantity and average cost, keeping the total cost.</li>
 *   <li>Bonus shares: add quantity at the {@link CorporateAction#assignedCost() assigned cost}.</li>
 *   <li>Fractional shares resulting from a corporate action are dropped (B3 auctions them), as if sold at
 *       the average cost. The auction proceeds are not part of the position.</li>
 * </ul>
 * Ordering: events are processed chronologically regardless of the input order. On the same date, trades
 * come first, then transfers, then corporate actions (they apply to whoever holds the asset at the end of the
 * record date). Events of the same kind on the same date keep their relative input order.
 * <p>
 * Merger: the position leaves the asset entirely (it is closed). Moving its cost to the successor asset involves two
 * histories, so it is done beforehand by {@link MergerResolver}. Amortization is not supported yet.
 * <p>
 * Specification: {@code PositionCalculatorTest}.
 */
public class PositionCalculator {

    /**
     * @param history trades, transfers and corporate actions of one asset, each list in any order
     * @return the current position; {@link Position#empty(Long)} when there are no trades
     * @throws IllegalArgumentException if an event belongs to another asset, a trade is not in BRL or its fee
     *     was paid in an asset
     * @throws InsufficientQuantityException if a sell or transfer exceeds the quantity held at that point in time
     * @throws UnsupportedOperationException for amortization
     */
    public Position calculate(AssetHistory history) {
        var position = Position.empty(history.assetId());
        for (Event event : timeline(history)) {
            position = switch (event) {
                case TradeEvent(Trade trade) -> applyTrade(position, trade);
                case TransferEvent(Transfer transfer) -> applyTransfer(position, transfer);
                case CorporateActionEvent(CorporateAction action) -> applyCorporateAction(position, action);
            };
        }
        return position;
    }

    // --- Timeline ---

    /**
     * Event of the asset history, ordered by date and then by {@link #rank()} (trades, transfers, corporate
     * actions). The sort is stable, so events of the same kind on the same date keep their input order.
     */
    private sealed interface Event {

        LocalDate date();

        int rank();
    }

    private record TradeEvent(Trade trade) implements Event {

        @Override
        public LocalDate date() {
            return trade.date();
        }

        @Override
        public int rank() {
            return 0;
        }
    }

    private record TransferEvent(Transfer transfer) implements Event {

        @Override
        public LocalDate date() {
            return transfer.date();
        }

        @Override
        public int rank() {
            return 1;
        }
    }

    private record CorporateActionEvent(CorporateAction action) implements Event {

        @Override
        public LocalDate date() {
            return action.recordDate();
        }

        @Override
        public int rank() {
            return 2;
        }
    }

    private static List<Event> timeline(AssetHistory history) {
        var events = new ArrayList<Event>();
        for (Trade trade : history.trades()) {
            validate(trade, history.assetId());
            events.add(new TradeEvent(trade));
        }
        for (Transfer transfer : history.transfers()) {
            requireSameAsset(transfer.assetId(), history.assetId(), "Transfer");
            events.add(new TransferEvent(transfer));
        }
        for (CorporateAction action : history.corporateActions()) {
            requireSameAsset(action.assetId(), history.assetId(), "Corporate action");
            events.add(new CorporateActionEvent(action));
        }
        events.sort(Comparator.comparing(Event::date).thenComparingInt(Event::rank));
        return events;
    }

    private static void validate(Trade trade, Long assetId) {
        requireSameAsset(trade.assetId(), assetId, "Trade");
        if (trade.priceCurrency() != Currency.BRL) {
            throw new IllegalArgumentException(
                    "Trade must be converted to BRL before position calculation: " + trade.priceCurrency());
        }
        if (trade.fee() instanceof TradeFee.InAsset) {
            throw new IllegalArgumentException(
                    "Fee paid in an asset must be converted to BRL before position calculation");
        }
    }

    private static void requireSameAsset(Long eventAssetId, Long assetId, String eventName) {
        if (!assetId.equals(eventAssetId)) {
            throw new IllegalArgumentException(
                    "%s of asset %d does not belong to asset %d".formatted(eventName, eventAssetId, assetId));
        }
    }

    // --- Trades ---

    private static Position applyTrade(Position position, Trade trade) {
        return switch (trade.type()) {
            case BUY -> buy(position, trade);
            case SELL -> sell(position, trade);
        };
    }

    /** Purchase fees enter the acquisition cost, so they raise the average cost. */
    private static Position buy(Position position, Trade trade) {
        var purchaseCost = trade.unitPrice().times(trade.quantity()).plus(feeAmount(trade));
        return addAtCost(position, trade.quantity(), purchaseCost);
    }

    /** Sale fees reduce the gain, not the cost: they are ignored here. */
    private static Position sell(Position position, Trade trade) {
        return removeAtAverageCost(position, trade.quantity(), "sell");
    }

    private static Money feeAmount(Trade trade) {
        return switch (trade.fee()) {
            case TradeFee.InCurrency(Money amount) -> amount;
            case TradeFee.InAsset _ -> throw new IllegalStateException("Fee in asset must have been rejected");
        };
    }

    // --- Transfers ---

    /** Moving between the user's own institutions keeps the position; only the fee leaves, at the average cost. */
    private static Position applyTransfer(Position position, Transfer transfer) {
        requireHeld(position, transfer.quantity(), "transfer");
        return removeAtAverageCost(position, transfer.fee(), "transfer fee");
    }

    // --- Corporate actions ---

    private static Position applyCorporateAction(Position position, CorporateAction action) {
        return switch (action.type()) {
            case STOCK_SPLIT -> position.isClosed() ? position : split(position, action.factor());
            case REVERSE_SPLIT -> position.isClosed() ? position : reverseSplit(position, action.factor());
            case BONUS_SHARES -> position.isClosed() ? position : bonus(position, action);
            case MERGER -> Position.empty(position.assetId());
            case AMORTIZATION -> throw new UnsupportedOperationException(
                    "Corporate action not supported yet: " + action.type());
        };
    }

    /** {@code factor} new shares per old share: quantity × factor, average cost ÷ factor, same total cost. */
    private static Position split(Position position, BigDecimal factor) {
        var split = new Position(position.assetId(), position.quantity().times(factor),
                position.averageCost().dividedBy(factor));
        return dropFraction(split);
    }

    /** {@code factor} old shares per new share: quantity ÷ factor, average cost × factor, same total cost. */
    private static Position reverseSplit(Position position, BigDecimal factor) {
        var grouped = new Position(position.assetId(), position.quantity().dividedBy(factor),
                position.averageCost().times(factor));
        return dropFraction(grouped);
    }

    /** {@code factor} bonus shares per share held, each at the assigned cost; fractions are not received. */
    private static Position bonus(Position position, CorporateAction action) {
        var bonusShares = position.quantity().times(action.factor()).truncated();
        if (bonusShares.isZero()) {
            return position;
        }
        return addAtCost(position, bonusShares, action.assignedCost().times(bonusShares));
    }

    /** The fraction is auctioned by B3: it leaves at the average cost, like a sale. */
    private static Position dropFraction(Position position) {
        return new Position(position.assetId(), position.quantity().truncated(), position.averageCost());
    }

    // --- Position arithmetic ---

    /** Weighted average: (current total cost + added cost) ÷ new quantity. */
    private static Position addAtCost(Position position, Quantity quantity, Money cost) {
        var newQuantity = position.quantity().plus(quantity);
        var newAverageCost = position.totalCost().plus(cost).dividedBy(newQuantity);
        return new Position(position.assetId(), newQuantity, newAverageCost);
    }

    /** Keeps the average cost; closing the position resets it. */
    private static Position removeAtAverageCost(Position position, Quantity quantity, String operation) {
        requireHeld(position, quantity, operation);
        var remaining = position.quantity().minus(quantity);
        if (remaining.isZero()) {
            return Position.empty(position.assetId());
        }
        return new Position(position.assetId(), remaining, position.averageCost());
    }

    private static void requireHeld(Position position, Quantity quantity, String operation) {
        if (quantity.isGreaterThan(position.quantity())) {
            throw new InsufficientQuantityException("Cannot %s %s of asset %d: only %s held".formatted(
                    operation, quantity, position.assetId(), position.quantity()));
        }
    }
}
