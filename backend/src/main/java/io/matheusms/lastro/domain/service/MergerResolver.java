package io.matheusms.lastro.domain.service;

import io.matheusms.lastro.domain.model.AssetHistory;
import io.matheusms.lastro.domain.model.CorporateAction;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.EntrySource;
import io.matheusms.lastro.domain.model.Trade;
import io.matheusms.lastro.domain.model.TradeFee;
import io.matheusms.lastro.domain.model.TradeType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Moves positions through mergers ("incorporação") and ticker conversions (pure function, BRL histories only).
 * <p>
 * A merger closes the position in the merged asset ({@link PositionCalculator}) and this step adds to the successor's
 * history an acquisition on the record date with {@code quantity × factor} shares and the whole cost of the merged
 * position. As with other corporate actions, the fraction is dropped (B3 auctions it) and leaves at the average
 * cost. The acquisition is not a cash flow. Mergers are applied in date order, so chains (A → B → C) work.
 */
public class MergerResolver {

    private final PositionCalculator calculator = new PositionCalculator();

    /**
     * @param histories BRL histories by asset id
     * @return the same histories, with the acquisitions added to the successors (created when absent)
     */
    public Map<Long, AssetHistory> resolve(Map<Long, AssetHistory> histories) {
        var mergers = histories.values().stream()
                .flatMap(history -> history.corporateActions().stream())
                .filter(CorporateAction::isMerger)
                .sorted(Comparator.comparing(CorporateAction::recordDate))
                .toList();
        if (mergers.isEmpty()) {
            return histories;
        }
        var resolved = new HashMap<>(histories);
        for (CorporateAction merger : mergers) {
            acquisition(resolved.get(merger.assetId()), merger).ifPresent(trade -> resolved.merge(
                    trade.assetId(),
                    new AssetHistory(trade.assetId(), List.of(trade), List.of(), List.of()),
                    (successor, ignored) -> withTrade(successor, trade)));
        }
        return resolved;
    }

    private Optional<Trade> acquisition(AssetHistory merged, CorporateAction merger) {
        var before = calculator.calculate(upTo(merged, merger));
        if (before.isClosed()) {
            return Optional.empty();
        }
        var received = before.quantity().times(merger.factor());
        var kept = received.truncated();
        if (kept.isZero()) {
            return Optional.empty();
        }
        var cost = before.totalCost().times(kept).dividedBy(received);
        var reference = merged.trades().getLast();
        return Optional.of(new Trade(null, reference.userId(), merger.successorAssetId(), reference.institutionId(),
                TradeType.BUY, merger.recordDate(), kept, cost.dividedBy(kept), TradeFee.none(Currency.BRL),
                EntrySource.MANUAL, null));
    }

    /** Everything up to the record date, except the merger itself (and any other merger on that date). */
    private static AssetHistory upTo(AssetHistory history, CorporateAction merger) {
        var date = merger.recordDate();
        return new AssetHistory(history.assetId(),
                history.trades().stream().filter(trade -> !trade.date().isAfter(date)).toList(),
                history.transfers().stream().filter(transfer -> !transfer.date().isAfter(date)).toList(),
                history.corporateActions().stream()
                        .filter(action -> action.recordDate().isBefore(date)
                                || (action.recordDate().equals(date) && !action.isMerger()))
                        .toList());
    }

    private static AssetHistory withTrade(AssetHistory history, Trade trade) {
        var trades = new ArrayList<>(history.trades());
        trades.add(trade);
        return new AssetHistory(history.assetId(), trades, history.transfers(), history.corporateActions());
    }
}
