package io.matheusms.lastro.domain.service;

import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.SuggestionItem;
import io.matheusms.lastro.domain.model.SuggestionReason;
import io.matheusms.lastro.domain.model.TargetAllocation;
import io.matheusms.lastro.domain.service.AllocationAnalyzer.Bucket;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rules engine of the contribution suggestion (pure and deterministic: the numbers never come from the LLM).
 * Rebalances only through the new money, never suggesting sales (they would trigger income tax and costs).
 * <ol>
 *   <li>Buckets: each target of the strategy (class or asset). Classes without a target get nothing.</li>
 *   <li>Each bucket's gap is how much it lacks to reach its target in the portfolio <em>after</em> the
 *       contribution. If the amount covers every gap, the gaps are closed and the rest is split in proportion
 *       to the targets; otherwise the amount is split in proportion to the gaps.</li>
 *   <li>Inside a class bucket the money goes to the assets already held, filling them towards equal weights and
 *       never above the per-asset cap ({@code maxPerAsset}). A class with no asset held, or whose assets all
 *       reached the cap, gets an item without asset: the investor picks one.</li>
 *   <li>Amounts are rounded to cents; the rounding difference goes to the largest item, so the items always add
 *       up to the contribution.</li>
 * </ol>
 */
public class ContributionPlanner {

    private static final MathContext PRECISION = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final AllocationAnalyzer analyzer = new AllocationAnalyzer();

    /** @return items ordered by amount, largest first */
    public List<SuggestionItem> plan(Collection<Holding> holdings, List<TargetAllocation> targets, Money amount) {
        if (!amount.isIn(Currency.BRL) || amount.amount().signum() <= 0) {
            throw new IllegalArgumentException("Contribution amount must be positive and in BRL");
        }
        var buckets = analyzer.analyze(holdings, targets).stream().filter(bucket -> bucket.target() != null).toList();
        var totalAfter = AllocationAnalyzer.sum(holdings).plus(amount);

        var gaps = new HashMap<Bucket, Money>();
        buckets.forEach(bucket -> gaps.put(bucket, positive(desired(bucket, totalAfter).minus(bucket.value()))));
        var totalGap = gaps.values().stream().reduce(zero(), Money::plus);

        var items = new ArrayList<SuggestionItem>();
        for (Bucket bucket : buckets) {
            var gap = gaps.get(bucket);
            Money bucketAmount;
            SuggestionReason reason;
            if (totalGap.isZero()) {
                bucketAmount = amount.times(share(bucket));
                reason = SuggestionReason.PROPORTIONAL_TO_TARGET;
            } else if (totalGap.compareTo(amount) <= 0) {
                bucketAmount = gap.plus(amount.minus(totalGap).times(share(bucket)));
                reason = gap.isZero() ? SuggestionReason.PROPORTIONAL_TO_TARGET : SuggestionReason.BELOW_TARGET;
            } else {
                bucketAmount = amount.times(ratio(gap, totalGap));
                reason = SuggestionReason.BELOW_TARGET;
            }
            if (!bucketAmount.isZero()) {
                items.addAll(splitInsideBucket(bucket, bucketAmount, reason, totalAfter));
            }
        }
        return roundToCents(items, amount);
    }

    private static Money desired(Bucket bucket, Money totalAfter) {
        return totalAfter.times(share(bucket));
    }

    private static BigDecimal share(Bucket bucket) {
        return bucket.targetPercentage().divide(HUNDRED, PRECISION);
    }

    private List<SuggestionItem> splitInsideBucket(Bucket bucket, Money amount, SuggestionReason reason, Money totalAfter) {
        var target = bucket.target();
        if (target.isAssetTarget()) {
            var assetClass = bucket.assetClass();
            if (assetClass == null) {
                throw new IllegalArgumentException(
                        "Asset %d has a target but is not held: its class is unknown".formatted(target.assetId()));
            }
            return List.of(item(bucket, assetClass, target.assetId(), amount, reason));
        }
        if (bucket.members().isEmpty()) {
            return List.of(item(bucket, target.assetClass(), null, amount, SuggestionReason.NEW_POSITION_IN_CLASS));
        }
        var cap = target.maxPerAsset() == null ? null : totalAfter.times(target.maxPerAsset().divide(HUNDRED, PRECISION));
        var allocation = fillTowardsEqualWeights(bucket.members(), amount, cap);
        var items = new ArrayList<SuggestionItem>();
        allocation.forEach((holding, value) -> {
            if (!value.isZero()) {
                items.add(item(bucket, holding.assetClass(), holding.assetId(), value, reason));
            }
        });
        var allocated = allocation.values().stream().reduce(zero(), Money::plus);
        var leftover = amount.minus(allocated);
        if (leftover.amount().signum() > 0) {
            items.add(item(bucket, target.assetClass(), null, leftover, SuggestionReason.ASSET_CAP_REACHED));
        }
        return items;
    }

    /**
     * Water-filling: each round gives every asset with room an equal share of what is left, limited by its gap to
     * the equal weight (first) and by the cap (always).
     */
    private static Map<Holding, Money> fillTowardsEqualWeights(List<Holding> members, Money amount, Money cap) {
        var classAfter = AllocationAnalyzer.sum(members).plus(amount);
        var equalWeight = classAfter.dividedBy(BigDecimal.valueOf(members.size()));
        var allocation = new LinkedHashMap<Holding, Money>();
        members.forEach(holding -> allocation.put(holding, zero()));

        // First towards equal weights, in proportion to each gap
        var gaps = new HashMap<Holding, Money>();
        for (Holding holding : members) {
            var gap = positive(equalWeight.minus(holding.value()));
            gaps.put(holding, cap == null ? gap : min(gap, positive(cap.minus(holding.value()))));
        }
        var totalGap = gaps.values().stream().reduce(zero(), Money::plus);
        var first = min(amount, totalGap);
        if (!totalGap.isZero()) {
            members.forEach(holding -> allocation.put(holding, first.times(ratio(gaps.get(holding), totalGap))));
        }

        // Then the rest equally among assets still under the cap
        var remaining = amount.minus(first);
        for (int round = 0; round < members.size() && remaining.amount().signum() > 0; round++) {
            var withRoom = members.stream()
                    .filter(holding -> cap == null || room(holding, allocation, cap).amount().signum() > 0)
                    .toList();
            if (withRoom.isEmpty()) {
                break;
            }
            var each = remaining.dividedBy(BigDecimal.valueOf(withRoom.size()));
            for (Holding holding : withRoom) {
                var given = cap == null ? each : min(each, room(holding, allocation, cap));
                allocation.put(holding, allocation.get(holding).plus(given));
                remaining = remaining.minus(given);
            }
        }
        return allocation;
    }

    private static Money room(Holding holding, Map<Holding, Money> allocation, Money cap) {
        return positive(cap.minus(holding.value()).minus(allocation.get(holding)));
    }

    private static SuggestionItem item(Bucket bucket, AssetClass assetClass, Long assetId, Money amount,
            SuggestionReason reason) {
        return new SuggestionItem(assetClass, assetId, amount, reason, bucket.currentPercentage(),
                bucket.targetPercentage());
    }

    private static List<SuggestionItem> roundToCents(List<SuggestionItem> items, Money amount) {
        var rounded = new ArrayList<SuggestionItem>();
        for (SuggestionItem item : items) {
            rounded.add(withAmount(item, item.suggestedAmount().roundedToCents()));
        }
        rounded.removeIf(item -> item.suggestedAmount().isZero());
        rounded.sort(Comparator.comparing((SuggestionItem item) -> item.suggestedAmount()).reversed());
        if (!rounded.isEmpty()) {
            var total = rounded.stream().map(SuggestionItem::suggestedAmount).reduce(zero(), Money::plus);
            var difference = amount.roundedToCents().minus(total);
            var largest = rounded.getFirst();
            rounded.set(0, withAmount(largest, largest.suggestedAmount().plus(difference)));
        }
        return rounded;
    }

    private static SuggestionItem withAmount(SuggestionItem item, Money amount) {
        return new SuggestionItem(item.assetClass(), item.assetId(), amount, item.reason(), item.currentPercentage(),
                item.targetPercentage());
    }

    private static BigDecimal ratio(Money part, Money whole) {
        return part.amount().divide(whole.amount(), PRECISION);
    }

    private static Money positive(Money money) {
        return money.isNegative() ? zero() : money;
    }

    private static Money min(Money first, Money second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private static Money zero() {
        return Money.zero(Currency.BRL);
    }
}
