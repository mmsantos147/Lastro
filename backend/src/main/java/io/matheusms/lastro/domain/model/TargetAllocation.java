package io.matheusms.lastro.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Target weight of a bucket of the portfolio: an asset class or a specific asset. A specific asset leaves its
 * class bucket, so every holding belongs to exactly one bucket.
 *
 * @param assetClass set for a class bucket; {@code null} for an asset bucket
 * @param assetId set for an asset bucket; {@code null} for a class bucket
 * @param targetPercentage share of the whole portfolio, in percent (25 = 25%)
 * @param minTolerance how many percentage points below the target still count as on target
 * @param maxTolerance how many percentage points above the target still count as on target
 * @param maxPerAsset cap for any single asset of a class bucket, in percent of the whole portfolio;
 *     {@code null} for no cap
 */
public record TargetAllocation(
        AssetClass assetClass,
        Long assetId,
        BigDecimal targetPercentage,
        BigDecimal minTolerance,
        BigDecimal maxTolerance,
        BigDecimal maxPerAsset) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public TargetAllocation {
        if ((assetClass == null) == (assetId == null)) {
            throw new IllegalArgumentException("A target is either an asset class or an asset");
        }
        Objects.requireNonNull(targetPercentage, "targetPercentage must not be null");
        Objects.requireNonNull(minTolerance, "minTolerance must not be null");
        Objects.requireNonNull(maxTolerance, "maxTolerance must not be null");
        if (targetPercentage.signum() < 0 || targetPercentage.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("Target percentage must be between 0 and 100");
        }
        if (minTolerance.signum() < 0 || maxTolerance.signum() < 0) {
            throw new IllegalArgumentException("Tolerances must not be negative");
        }
        if (maxPerAsset != null && (maxPerAsset.signum() <= 0 || maxPerAsset.compareTo(HUNDRED) > 0)) {
            throw new IllegalArgumentException("Max per asset must be between 0 (exclusive) and 100");
        }
        if (maxPerAsset != null && assetId != null) {
            throw new IllegalArgumentException("Max per asset applies only to asset class targets");
        }
    }

    public static TargetAllocation forClass(AssetClass assetClass, String target, String tolerance) {
        var points = new BigDecimal(tolerance);
        return new TargetAllocation(assetClass, null, new BigDecimal(target), points, points, null);
    }

    public boolean isAssetTarget() {
        return assetId != null;
    }
}
