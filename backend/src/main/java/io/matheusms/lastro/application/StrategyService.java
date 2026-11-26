package io.matheusms.lastro.application;

import io.matheusms.lastro.domain.model.Strategy;
import io.matheusms.lastro.domain.model.TargetAllocation;
import io.matheusms.lastro.domain.port.AssetRepository;
import io.matheusms.lastro.domain.port.ContributionSuggestionRepository;
import io.matheusms.lastro.domain.port.StrategyRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Target allocations of the user. At most one is active; none is created automatically. */
@Service
public class StrategyService {

    private final StrategyRepository strategies;
    private final ContributionSuggestionRepository suggestions;
    private final AssetRepository assets;

    StrategyService(StrategyRepository strategies, ContributionSuggestionRepository suggestions,
            AssetRepository assets) {
        this.strategies = strategies;
        this.suggestions = suggestions;
        this.assets = assets;
    }

    @Transactional(readOnly = true)
    public List<Strategy> list(Long userId) {
        return strategies.findByUser(userId);
    }

    @Transactional(readOnly = true)
    public Optional<Strategy> active(Long userId) {
        return strategies.findActive(userId);
    }

    /** Creates a strategy; when {@code active}, it replaces the current active one. */
    @Transactional
    public Strategy create(Long userId, String name, boolean active, List<TargetAllocation> targets) {
        requireAssets(targets);
        if (active) {
            deactivateCurrent(userId);
        }
        return strategies.save(new Strategy(null, userId, name, active, targets));
    }

    @Transactional
    public Strategy update(Long userId, Long strategyId, String name, List<TargetAllocation> targets) {
        var current = owned(userId, strategyId);
        requireAssets(targets);
        return strategies.save(new Strategy(strategyId, userId, name, current.active(), targets));
    }

    @Transactional
    public Strategy activate(Long userId, Long strategyId) {
        var strategy = owned(userId, strategyId);
        if (strategy.active()) {
            return strategy;
        }
        deactivateCurrent(userId);
        return strategies.save(strategy.withActive(true));
    }

    @Transactional
    public Strategy deactivate(Long userId, Long strategyId) {
        return strategies.save(owned(userId, strategyId).withActive(false));
    }

    /** @throws ConflictException if suggestions were computed with it (deactivate it instead) */
    @Transactional
    public void delete(Long userId, Long strategyId) {
        owned(userId, strategyId);
        if (suggestions.existsByStrategy(strategyId)) {
            throw new ConflictException("Strategy %d has contribution suggestions; deactivate it instead"
                    .formatted(strategyId));
        }
        strategies.deleteById(strategyId);
    }

    private void deactivateCurrent(Long userId) {
        // Saved (and flushed) before the new active one because of the one-active-per-user unique index
        strategies.findActive(userId).ifPresent(current -> strategies.save(current.withActive(false)));
    }

    private Strategy owned(Long userId, Long strategyId) {
        return strategies.findById(strategyId)
                .filter(strategy -> strategy.userId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Strategy", strategyId));
    }

    private void requireAssets(List<TargetAllocation> targets) {
        targets.stream().filter(TargetAllocation::isAssetTarget).forEach(target -> {
            if (assets.findById(target.assetId()).isEmpty()) {
                throw new NotFoundException("Asset", target.assetId());
            }
        });
    }
}
