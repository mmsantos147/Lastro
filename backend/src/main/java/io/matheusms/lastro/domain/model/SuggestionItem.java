package io.matheusms.lastro.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One line of a contribution suggestion.
 *
 * @param assetId {@code null} when the investor must pick the asset of the class
 * @param currentPercentage weight of the item's bucket (class or asset) before the contribution, in percent
 * @param targetPercentage target of the item's bucket, in percent
 */
public record SuggestionItem(
        AssetClass assetClass,
        Long assetId,
        Money suggestedAmount,
        SuggestionReason reason,
        BigDecimal currentPercentage,
        BigDecimal targetPercentage) {

    public SuggestionItem {
        Objects.requireNonNull(assetClass, "assetClass must not be null");
        Objects.requireNonNull(suggestedAmount, "suggestedAmount must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(currentPercentage, "currentPercentage must not be null");
        Objects.requireNonNull(targetPercentage, "targetPercentage must not be null");
        if (suggestedAmount.isNegative()) {
            throw new IllegalArgumentException("Suggested amount must not be negative");
        }
    }
}
