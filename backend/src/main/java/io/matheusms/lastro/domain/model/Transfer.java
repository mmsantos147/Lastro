package io.matheusms.lastro.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Movement of an asset between two places owned by the user (e.g. from an exchange to the user's wallet).
 * Does not change the average cost; the fee, paid in the asset itself, reduces the quantity held.
 *
 * @param id {@code null} until persisted
 * @param quantity quantity that left the origin; {@code quantity - fee} arrives at the destination
 * @param fee network/withdrawal fee in units of the transferred asset
 * @param externalId identifier given by the source (e.g. Binance withdrawal id); {@code null} when not available
 */
public record Transfer(
        Long id,
        Long userId,
        Long assetId,
        Long fromInstitutionId,
        Long toInstitutionId,
        LocalDate date,
        Quantity quantity,
        Quantity fee,
        EntrySource source,
        String externalId) {

    public Transfer {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(assetId, "assetId must not be null");
        Objects.requireNonNull(fromInstitutionId, "fromInstitutionId must not be null");
        Objects.requireNonNull(toInstitutionId, "toInstitutionId must not be null");
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(quantity, "quantity must not be null");
        Objects.requireNonNull(fee, "fee must not be null");
        Objects.requireNonNull(source, "source must not be null");
        if (fromInstitutionId.equals(toInstitutionId)) {
            throw new IllegalArgumentException("Transfer origin and destination must differ");
        }
        if (quantity.isZero()) {
            throw new IllegalArgumentException("Transfer quantity must be positive");
        }
        if (fee.isGreaterThan(quantity)) {
            throw new IllegalArgumentException("Transfer fee must not exceed the transferred quantity");
        }
    }

    public Quantity receivedQuantity() {
        return quantity.minus(fee);
    }
}
