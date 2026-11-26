package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Distribution;
import io.matheusms.lastro.domain.model.DistributionType;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quantity;
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
@Table(name = "distribution")
class DistributionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "institution_id", nullable = false)
    private Long institutionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DistributionType type;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "base_quantity", nullable = false, precision = 20, scale = 8)
    private BigDecimal baseQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(name = "gross_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal grossAmount;

    @Column(name = "withheld_tax", nullable = false, precision = 20, scale = 8)
    private BigDecimal withheldTax;

    @Column(name = "net_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal netAmount;

    @Column(name = "paid_in_asset_id")
    private Long paidInAssetId;

    @Column(name = "paid_quantity", precision = 20, scale = 8)
    private BigDecimal paidQuantity;

    protected DistributionEntity() {
    }

    static DistributionEntity from(Distribution distribution) {
        var entity = new DistributionEntity();
        entity.id = distribution.id();
        entity.userId = distribution.userId();
        entity.assetId = distribution.assetId();
        entity.institutionId = distribution.institutionId();
        entity.type = distribution.type();
        entity.recordDate = distribution.recordDate();
        entity.paymentDate = distribution.paymentDate();
        entity.baseQuantity = distribution.baseQuantity().value();
        entity.currency = distribution.grossAmount().currency();
        entity.grossAmount = distribution.grossAmount().amount();
        entity.withheldTax = distribution.withheldTax().amount();
        entity.netAmount = distribution.netAmount().amount();
        entity.paidInAssetId = distribution.paidInAssetId();
        entity.paidQuantity = distribution.paidQuantity() == null ? null : distribution.paidQuantity().value();
        return entity;
    }

    Distribution toDomain() {
        return new Distribution(id, userId, assetId, institutionId, type, recordDate, paymentDate,
                new Quantity(baseQuantity),
                new Money(grossAmount, currency), new Money(withheldTax, currency), new Money(netAmount, currency),
                paidInAssetId, paidQuantity == null ? null : new Quantity(paidQuantity));
    }
}
