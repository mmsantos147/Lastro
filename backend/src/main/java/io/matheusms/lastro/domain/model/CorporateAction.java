package io.matheusms.lastro.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Event that changes a position without a trade. Applies to whoever holds the asset at the end of the
 * record date ("data com"), so trades on the record date happen before the event.
 * <p>
 * Meaning of {@code factor} by type:
 * <ul>
 *   <li>{@link CorporateActionType#STOCK_SPLIT}: new shares per old share ({@code 2} for a 1:2 split).</li>
 *   <li>{@link CorporateActionType#REVERSE_SPLIT}: old shares per new share ({@code 10} for a 10:1 reverse split).</li>
 *   <li>{@link CorporateActionType#BONUS_SHARES}: bonus shares per share held ({@code 0.1} for 10%).</li>
 *   <li>{@link CorporateActionType#MERGER}: shares of the successor received per share held ({@code 0.5} when
 *       every 2 shares become 1). Also covers ticker conversions (factor {@code 1}).</li>
 * </ul>
 *
 * @param id {@code null} until persisted
 * @param assignedCost cost per bonus share set by the company ("custo atribuído"), in BRL, which enters the
 *     average cost; required for {@link CorporateActionType#BONUS_SHARES} only
 * @param successorAssetId asset received in a merger ("incorporação"), which inherits the total cost; required for
 *     {@link CorporateActionType#MERGER} only
 */
public record CorporateAction(
        Long id,
        Long assetId,
        CorporateActionType type,
        LocalDate recordDate,
        BigDecimal factor,
        Money assignedCost,
        Long successorAssetId) {

    public CorporateAction {
        Objects.requireNonNull(assetId, "assetId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(recordDate, "recordDate must not be null");
        Objects.requireNonNull(factor, "factor must not be null");
        if (factor.signum() <= 0) {
            throw new IllegalArgumentException("Corporate action factor must be positive");
        }
        if (type == CorporateActionType.BONUS_SHARES && assignedCost == null) {
            throw new IllegalArgumentException("Bonus shares require an assigned cost");
        }
        if (assignedCost != null && assignedCost.isNegative()) {
            throw new IllegalArgumentException("Assigned cost must not be negative");
        }
        if (assignedCost != null && !assignedCost.isIn(Currency.BRL)) {
            throw new IllegalArgumentException("Assigned cost must be in BRL");
        }
        if ((type == CorporateActionType.MERGER) != (successorAssetId != null)) {
            throw new IllegalArgumentException("A successor asset is required for mergers, and only for them");
        }
        if (assetId.equals(successorAssetId)) {
            throw new IllegalArgumentException("The successor asset must differ from the merged asset");
        }
    }

    /** Any corporate action but a merger. */
    public CorporateAction(
            Long id, Long assetId, CorporateActionType type, LocalDate recordDate, BigDecimal factor,
            Money assignedCost) {
        this(id, assetId, type, recordDate, factor, assignedCost, null);
    }

    public boolean isMerger() {
        return type == CorporateActionType.MERGER;
    }
}
