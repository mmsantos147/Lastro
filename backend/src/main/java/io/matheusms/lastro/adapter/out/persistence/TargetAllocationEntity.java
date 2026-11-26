package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.TargetAllocation;
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
@Table(name = "target_allocation")
class TargetAllocationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_class", length = 20)
    private AssetClass assetClass;

    @Column(name = "asset_id")
    private Long assetId;

    @Column(name = "target_percentage", nullable = false, precision = 7, scale = 4)
    private BigDecimal targetPercentage;

    @Column(name = "min_tolerance", nullable = false, precision = 7, scale = 4)
    private BigDecimal minTolerance;

    @Column(name = "max_tolerance", nullable = false, precision = 7, scale = 4)
    private BigDecimal maxTolerance;

    @Column(name = "max_per_asset", precision = 7, scale = 4)
    private BigDecimal maxPerAsset;

    protected TargetAllocationEntity() {
    }

    static TargetAllocationEntity from(TargetAllocation target) {
        var entity = new TargetAllocationEntity();
        entity.assetClass = target.assetClass();
        entity.assetId = target.assetId();
        entity.targetPercentage = target.targetPercentage();
        entity.minTolerance = target.minTolerance();
        entity.maxTolerance = target.maxTolerance();
        entity.maxPerAsset = target.maxPerAsset();
        return entity;
    }

    TargetAllocation toDomain() {
        return new TargetAllocation(assetClass, assetId, targetPercentage, minTolerance, maxTolerance, maxPerAsset);
    }
}
