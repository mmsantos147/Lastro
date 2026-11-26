package io.matheusms.lastro.domain.model;

import static io.matheusms.lastro.domain.model.Preconditions.requireNotBlank;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Target allocation of a user's portfolio. Only one strategy is active at a time; without an active one the
 * application still monitors the portfolio but cannot suggest contributions.
 *
 * @param id {@code null} until persisted
 * @param targets buckets whose target percentages add up to 100
 */
public record Strategy(Long id, Long userId, String name, boolean active, List<TargetAllocation> targets) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public Strategy {
        Objects.requireNonNull(userId, "userId must not be null");
        requireNotBlank(name, "name");
        targets = List.copyOf(targets);
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("A strategy needs at least one target");
        }
        var total = targets.stream().map(TargetAllocation::targetPercentage).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(HUNDRED) != 0) {
            throw new IllegalArgumentException("Target percentages must add up to 100, got " + total.toPlainString());
        }
        var buckets = new HashSet<Object>();
        for (TargetAllocation target : targets) {
            Object bucket = target.isAssetTarget() ? target.assetId() : target.assetClass();
            if (!buckets.add(bucket)) {
                throw new IllegalArgumentException("Duplicated target: " + bucket);
            }
        }
    }

    public Strategy withActive(boolean active) {
        return new Strategy(id, userId, name, active, targets);
    }
}
