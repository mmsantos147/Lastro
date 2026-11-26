package io.matheusms.lastro.application;

import io.matheusms.lastro.domain.model.ContributionSuggestion;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.SuggestionSource;
import io.matheusms.lastro.domain.port.ContributionSuggestionRepository;
import io.matheusms.lastro.domain.port.StrategyRepository;
import io.matheusms.lastro.domain.service.ContributionPlanner;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the rules engine for a contribution and persists the result, both for the website and for Claude (MCP),
 * so an analysis can always point to the exact numbers it commented on.
 */
@Service
public class ContributionService {

    private final AllocationService allocation;
    private final StrategyRepository strategies;
    private final ContributionSuggestionRepository suggestions;
    private final Clock clock;
    private final ContributionPlanner planner = new ContributionPlanner();

    ContributionService(AllocationService allocation, StrategyRepository strategies,
            ContributionSuggestionRepository suggestions, Clock clock) {
        this.allocation = allocation;
        this.strategies = strategies;
        this.suggestions = suggestions;
        this.clock = clock;
    }

    /**
     * @return empty when the user has no active strategy: there is no target to rebalance towards
     */
    @Transactional
    public Optional<ContributionSuggestion> suggest(Long userId, Money amount, SuggestionSource source) {
        var strategy = strategies.findActive(userId);
        if (strategy.isEmpty()) {
            return Optional.empty();
        }
        var targets = strategy.get().targets();
        var items = planner.plan(allocation.holdings(userId, targets), targets, amount);
        return Optional.of(suggestions.save(new ContributionSuggestion(null, userId, strategy.get().id(),
                Instant.now(clock), amount, source, items)));
    }

    @Transactional(readOnly = true)
    public ContributionSuggestion get(Long userId, Long suggestionId) {
        return find(userId, suggestionId).orElseThrow(() -> new NotFoundException("Contribution suggestion", suggestionId));
    }

    @Transactional(readOnly = true)
    public Optional<ContributionSuggestion> find(Long userId, Long suggestionId) {
        return suggestions.findById(suggestionId).filter(suggestion -> suggestion.userId().equals(userId));
    }
}
