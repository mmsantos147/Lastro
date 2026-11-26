package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.SuggestionItem;
import io.matheusms.lastro.domain.model.SuggestionReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "suggestion_item")
class SuggestionItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Order of the items in the suggestion (largest amount first). */
    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_class", nullable = false, length = 20)
    private AssetClass assetClass;

    @Column(name = "asset_id")
    private Long assetId;

    @Column(name = "suggested_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal suggestedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SuggestionReason reason;

    @Column(name = "current_percentage", nullable = false, precision = 7, scale = 4)
    private BigDecimal currentPercentage;

    @Column(name = "target_percentage", nullable = false, precision = 7, scale = 4)
    private BigDecimal targetPercentage;

    protected SuggestionItemEntity() {
    }

    static SuggestionItemEntity from(int position, SuggestionItem item) {
        var entity = new SuggestionItemEntity();
        entity.position = position;
        entity.assetClass = item.assetClass();
        entity.assetId = item.assetId();
        entity.suggestedAmount = item.suggestedAmount().amount();
        entity.reason = item.reason();
        entity.currentPercentage = item.currentPercentage();
        entity.targetPercentage = item.targetPercentage();
        return entity;
    }

    SuggestionItem toDomain() {
        return new SuggestionItem(assetClass, assetId, new Money(suggestedAmount, Currency.BRL), reason,
                currentPercentage, targetPercentage);
    }
}
