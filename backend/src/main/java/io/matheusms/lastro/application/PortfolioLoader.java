package io.matheusms.lastro.application;

import io.matheusms.lastro.domain.model.AssetHistory;
import io.matheusms.lastro.domain.model.CorporateAction;
import io.matheusms.lastro.domain.model.Distribution;
import io.matheusms.lastro.domain.model.IndicatorType;
import io.matheusms.lastro.domain.model.Trade;
import io.matheusms.lastro.domain.model.TradeFee;
import io.matheusms.lastro.domain.port.CorporateActionRepository;
import io.matheusms.lastro.domain.port.DistributionRepository;
import io.matheusms.lastro.domain.port.EconomicIndicatorRepository;
import io.matheusms.lastro.domain.port.QuoteRepository;
import io.matheusms.lastro.domain.port.TradeRepository;
import io.matheusms.lastro.domain.port.TransferRepository;
import io.matheusms.lastro.domain.service.MarketDataSnapshot;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;

/** Loads everything a user's positions are derived from, plus the market data to value them. */
@Component
class PortfolioLoader {

    /** Lower bound for "all dates" in queries (PostgreSQL rejects {@link LocalDate#MIN}). */
    private static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);

    private final TradeRepository trades;
    private final TransferRepository transfers;
    private final CorporateActionRepository corporateActions;
    private final DistributionRepository distributions;
    private final QuoteRepository quotes;
    private final EconomicIndicatorRepository indicators;

    PortfolioLoader(TradeRepository trades, TransferRepository transfers, CorporateActionRepository corporateActions,
            DistributionRepository distributions, QuoteRepository quotes, EconomicIndicatorRepository indicators) {
        this.trades = trades;
        this.transfers = transfers;
        this.corporateActions = corporateActions;
        this.distributions = distributions;
        this.quotes = quotes;
        this.indicators = indicators;
    }

    /** Histories of every traded asset, plus the successors of their mergers (which may have no trades). */
    UserPortfolio load(Long userId) {
        var histories = new HashMap<Long, AssetHistory>();
        var pending = new ArrayDeque<>(trades.findTradedAssetIds(userId));
        while (!pending.isEmpty()) {
            var assetId = pending.poll();
            if (histories.containsKey(assetId)) {
                continue;
            }
            var history = history(userId, assetId);
            histories.put(assetId, history);
            history.corporateActions().stream()
                    .filter(CorporateAction::isMerger)
                    .forEach(merger -> pending.add(merger.successorAssetId()));
        }
        return new UserPortfolio(histories, distributions.findByUser(userId));
    }

    /** Stored history of one asset (empty lists when the user never traded it). */
    AssetHistory history(Long userId, Long assetId) {
        return new AssetHistory(assetId, trades.findByUserAndAsset(userId, assetId),
                transfers.findByUserAndAsset(userId, assetId), corporateActions.findByAsset(assetId));
    }

    /** Quotes of every asset the portfolio touches and the USD/BRL rates, up to {@code upTo}. */
    MarketDataSnapshot marketData(UserPortfolio portfolio, LocalDate upTo) {
        return new MarketDataSnapshot(
                quotes.findByAssetsUpTo(portfolio.referencedAssetIds(), upTo),
                indicators.findByTypeBetween(IndicatorType.USD_BRL, EARLIEST, upTo));
    }

    /**
     * @param histories stored histories by asset id, for every asset the user has traded
     * @param distributions every distribution of the user
     */
    record UserPortfolio(Map<Long, AssetHistory> histories, List<Distribution> distributions) {

        UserPortfolio {
            histories = Map.copyOf(histories);
            distributions = List.copyOf(distributions);
        }

        /** The same portfolio with one asset's history changed (an empty history when the asset was not traded). */
        UserPortfolio withHistory(Long assetId, UnaryOperator<AssetHistory> change) {
            var updated = new HashMap<>(histories);
            var current = histories.getOrDefault(assetId, new AssetHistory(assetId, List.of(), List.of(), List.of()));
            updated.put(assetId, change.apply(current));
            return new UserPortfolio(updated, distributions);
        }

        UserPortfolio withDistributions(List<Distribution> changed) {
            return new UserPortfolio(histories, changed);
        }

        /** Traded assets, assets in which fees were paid and assets received as rewards. */
        Set<Long> referencedAssetIds() {
            var ids = new HashSet<>(histories.keySet());
            histories.values().stream()
                    .flatMap(history -> history.trades().stream())
                    .map(Trade::fee)
                    .forEach(fee -> {
                        if (fee instanceof TradeFee.InAsset(Long feeAssetId, _)) {
                            ids.add(feeAssetId);
                        }
                    });
            distributions.stream()
                    .filter(distribution -> distribution.paidInAssetId() != null)
                    .forEach(distribution -> ids.add(distribution.paidInAssetId()));
            return ids;
        }
    }
}
