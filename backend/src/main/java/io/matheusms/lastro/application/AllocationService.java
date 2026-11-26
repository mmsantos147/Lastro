package io.matheusms.lastro.application;

import io.matheusms.lastro.application.PositionService.AssetPosition;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Strategy;
import io.matheusms.lastro.domain.model.TargetAllocation;
import io.matheusms.lastro.domain.port.AssetRepository;
import io.matheusms.lastro.domain.port.StrategyRepository;
import io.matheusms.lastro.domain.service.AllocationAnalyzer.Bucket;
import io.matheusms.lastro.domain.service.AllocationAnalyzer;
import io.matheusms.lastro.domain.service.Holding;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Current allocation compared with the active strategy. */
@Service
public class AllocationService {

    private final PositionService positions;
    private final StrategyRepository strategies;
    private final AssetRepository assets;
    private final AllocationAnalyzer analyzer = new AllocationAnalyzer();

    AllocationService(PositionService positions, StrategyRepository strategies, AssetRepository assets) {
        this.positions = positions;
        this.strategies = strategies;
        this.assets = assets;
    }

    /**
     * @return buckets vs target; without an active strategy, one bucket per class held, all {@code NO_TARGET}
     */
    @Transactional(readOnly = true)
    public Allocation allocationVsTarget(Long userId) {
        var strategy = strategies.findActive(userId);
        var holdings = holdings(userId, strategy.map(Strategy::targets).orElse(List.of()));
        var buckets = analyzer.analyze(holdings, strategy.map(Strategy::targets).orElse(List.of()));
        var total = holdings.stream().map(Holding::value).reduce(Money.zero(Currency.BRL), Money::plus);
        return new Allocation(strategy.orElse(null), total, buckets);
    }

    /**
     * Holdings valued at market (or at cost without a quote), plus zero holdings for assets that have their own
     * target but are not held yet, so the engine knows their class.
     */
    List<Holding> holdings(Long userId, List<TargetAllocation> targets) {
        var holdings = new ArrayList<Holding>();
        for (AssetPosition position : positions.openPositions(userId)) {
            holdings.add(new Holding(position.asset().id(), position.asset().assetClass(), position.marketValue()));
        }
        targets.stream()
                .filter(TargetAllocation::isAssetTarget)
                .filter(target -> holdings.stream().noneMatch(holding -> holding.assetId().equals(target.assetId())))
                .forEach(target -> assets.findById(target.assetId()).ifPresent(asset ->
                        holdings.add(new Holding(asset.id(), asset.assetClass(), Money.zero(Currency.BRL)))));
        return holdings;
    }

    /** @param strategy {@code null} when no strategy is active */
    public record Allocation(Strategy strategy, Money total, List<Bucket> buckets) {
    }
}
