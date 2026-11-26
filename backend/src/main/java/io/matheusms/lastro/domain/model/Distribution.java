package io.matheusms.lastro.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Income received from an asset ("provento").
 *
 * @param id {@code null} until persisted
 * @param recordDate last day to hold the asset to be entitled to the distribution ("data com")
 * @param baseQuantity quantity held on the record date
 * @param grossAmount gross value; for rewards paid in an asset, its value on the payment date
 * @param withheldTax income tax withheld at source (e.g. 15% on JCP)
 * @param netAmount must equal {@code grossAmount - withheldTax}
 * @param paidInAssetId asset in which the reward was paid; set together with {@code paidQuantity}, and only then
 */
public record Distribution(
        Long id,
        Long userId,
        Long assetId,
        Long institutionId,
        DistributionType type,
        LocalDate recordDate,
        LocalDate paymentDate,
        Quantity baseQuantity,
        Money grossAmount,
        Money withheldTax,
        Money netAmount,
        Long paidInAssetId,
        Quantity paidQuantity) {

    public Distribution {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(assetId, "assetId must not be null");
        Objects.requireNonNull(institutionId, "institutionId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(recordDate, "recordDate must not be null");
        Objects.requireNonNull(paymentDate, "paymentDate must not be null");
        Objects.requireNonNull(baseQuantity, "baseQuantity must not be null");
        Objects.requireNonNull(grossAmount, "grossAmount must not be null");
        Objects.requireNonNull(withheldTax, "withheldTax must not be null");
        Objects.requireNonNull(netAmount, "netAmount must not be null");
        if (grossAmount.isNegative() || withheldTax.isNegative()) {
            throw new IllegalArgumentException("Distribution amounts must not be negative");
        }
        if (!netAmount.equals(grossAmount.minus(withheldTax))) {
            throw new IllegalArgumentException(
                    "Net amount %s must equal gross amount %s minus withheld tax %s"
                            .formatted(netAmount, grossAmount, withheldTax));
        }
        if (paymentDate.isBefore(recordDate)) {
            throw new IllegalArgumentException("Payment date must not be before record date");
        }
        if ((paidInAssetId == null) != (paidQuantity == null)) {
            throw new IllegalArgumentException("paidInAssetId and paidQuantity must be set together");
        }
        if (paidQuantity != null && paidQuantity.isZero()) {
            throw new IllegalArgumentException("paidQuantity must be positive");
        }
        if (type == DistributionType.STAKING_REWARD && paidInAssetId == null) {
            throw new IllegalArgumentException("Staking rewards require the asset in which they were paid");
        }
    }
}
