package io.matheusms.lastro.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic result of the rules engine for a contribution, persisted every time it runs so an analysis
 * written by Claude can point to the exact numbers it commented on.
 *
 * @param id {@code null} until persisted
 */
public record ContributionSuggestion(
        Long id,
        Long userId,
        Long strategyId,
        Instant createdAt,
        Money amount,
        SuggestionSource source,
        List<SuggestionItem> items) {

    public ContributionSuggestion {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(strategyId, "strategyId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(source, "source must not be null");
        items = List.copyOf(items);
        if (!amount.isIn(Currency.BRL) || amount.amount().signum() <= 0) {
            throw new IllegalArgumentException("Contribution amount must be positive and in BRL");
        }
    }
}
