package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.CorporateAction;
import io.matheusms.lastro.domain.model.CorporateActionType;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "corporate_action")
class CorporateActionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CorporateActionType type;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal factor;

    /** Always BRL (see {@link CorporateAction#assignedCost()}). */
    @Column(name = "assigned_cost", precision = 20, scale = 8)
    private BigDecimal assignedCost;

    @Column(name = "successor_asset_id")
    private Long successorAssetId;

    protected CorporateActionEntity() {
    }

    static CorporateActionEntity from(CorporateAction action) {
        var entity = new CorporateActionEntity();
        entity.id = action.id();
        entity.assetId = action.assetId();
        entity.type = action.type();
        entity.recordDate = action.recordDate();
        entity.factor = action.factor();
        entity.assignedCost = action.assignedCost() == null ? null : action.assignedCost().amount();
        entity.successorAssetId = action.successorAssetId();
        return entity;
    }

    CorporateAction toDomain() {
        return new CorporateAction(id, assetId, type, recordDate, factor,
                assignedCost == null ? null : new Money(assignedCost, Currency.BRL), successorAssetId);
    }
}
