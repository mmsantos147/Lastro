package io.matheusms.lastro.domain.service;

import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.TargetAllocation;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Splits the portfolio into the buckets of a strategy (asset classes or specific assets) and compares each with
 * its target. Classes held without a target appear as {@link Status#NO_TARGET}. Pure function.
 */
public class AllocationAnalyzer {

    static final MathContext PRECISION = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public List<Bucket> analyze(Collection<Holding> holdings, List<TargetAllocation> targets) {
        var total = sum(holdings);
        Set<Long> assetsWithOwnTarget = targets.stream()
                .filter(TargetAllocation::isAssetTarget)
                .map(TargetAllocation::assetId)
                .collect(Collectors.toSet());

        var buckets = new ArrayList<Bucket>();
        var classesWithTarget = EnumSet.noneOf(AssetClass.class);
        for (TargetAllocation target : targets) {
            List<Holding> members = holdings.stream()
                    .filter(holding -> target.isAssetTarget()
                            ? holding.assetId().equals(target.assetId())
                            : holding.assetClass() == target.assetClass()
                                    && !assetsWithOwnTarget.contains(holding.assetId()))
                    .toList();
            if (!target.isAssetTarget()) {
                classesWithTarget.add(target.assetClass());
            }
            buckets.add(bucket(target, members, total));
        }
        for (AssetClass assetClass : AssetClass.values()) {
            var members = holdings.stream()
                    .filter(holding -> holding.assetClass() == assetClass
                            && !assetsWithOwnTarget.contains(holding.assetId()))
                    .toList();
            if (!classesWithTarget.contains(assetClass) && !members.isEmpty()) {
                buckets.add(new Bucket(null, assetClass, null, members, sum(members), percent(sum(members), total),
                        BigDecimal.ZERO, Status.NO_TARGET));
            }
        }
        return buckets;
    }

    private static Bucket bucket(TargetAllocation target, List<Holding> members, Money total) {
        var value = sum(members);
        var current = percent(value, total);
        var deviation = current.subtract(target.targetPercentage());
        Status status;
        if (deviation.compareTo(target.minTolerance().negate()) < 0) {
            status = Status.BELOW;
        } else if (deviation.compareTo(target.maxTolerance()) > 0) {
            status = Status.ABOVE;
        } else {
            status = Status.WITHIN;
        }
        var assetClass = target.isAssetTarget()
                ? members.stream().map(Holding::assetClass).findFirst().orElse(null)
                : target.assetClass();
        return new Bucket(target, assetClass, target.assetId(), members, value, current, target.targetPercentage(),
                status);
    }

    static Money sum(Collection<Holding> holdings) {
        return holdings.stream().map(Holding::value).reduce(Money.zero(Currency.BRL), Money::plus);
    }

    /** {@code value / total} in percent, 4 decimal places; zero for an empty portfolio. */
    static BigDecimal percent(Money value, Money total) {
        if (total.isZero()) {
            return BigDecimal.ZERO.setScale(4);
        }
        return value.amount().multiply(HUNDRED).divide(total.amount(), PRECISION).setScale(4, RoundingMode.HALF_EVEN);
    }

    public enum Status {
        BELOW,
        WITHIN,
        ABOVE,
        /** Held but not part of the strategy (its target is zero). */
        NO_TARGET
    }

    /**
     * @param target {@code null} for a class held without a target
     * @param assetClass the class of the bucket; for an asset bucket of an asset not held, {@code null}
     * @param assetId set for an asset bucket
     * @param members holdings in the bucket
     * @param currentPercentage share of the portfolio, in percent
     * @param targetPercentage in percent; zero without a target
     */
    public record Bucket(
            TargetAllocation target,
            AssetClass assetClass,
            Long assetId,
            List<Holding> members,
            Money value,
            BigDecimal currentPercentage,
            BigDecimal targetPercentage,
            Status status) {

        public BigDecimal deviation() {
            return currentPercentage.subtract(targetPercentage);
        }
    }
}
