package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.FixedIncomeIndexer;
import io.matheusms.lastro.domain.model.FixedIncomeTerms;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Subtype table of {@code asset} for private fixed income, keyed by the asset id (ADR 0002 and 0010). */
@Entity
@Table(name = "fixed_income_security")
class FixedIncomeSecurityEntity {

    @Id
    @Column(name = "asset_id")
    private Long assetId;

    @Column(length = 150)
    private String issuer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FixedIncomeIndexer indexer;

    @Column(name = "indexer_percentage", precision = 7, scale = 4)
    private BigDecimal indexerPercentage;

    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal rate;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "daily_liquidity", nullable = false)
    private boolean dailyLiquidity;

    protected FixedIncomeSecurityEntity() {
    }

    static FixedIncomeSecurityEntity from(FixedIncomeTerms terms) {
        var entity = new FixedIncomeSecurityEntity();
        entity.assetId = terms.assetId();
        entity.issuer = terms.issuer();
        entity.indexer = terms.indexer();
        entity.indexerPercentage = terms.indexerPercentage();
        entity.rate = terms.rate();
        entity.maturityDate = terms.maturityDate();
        entity.dailyLiquidity = terms.dailyLiquidity();
        return entity;
    }

    FixedIncomeTerms toDomain() {
        return new FixedIncomeTerms(assetId, issuer, indexer, indexerPercentage, rate, maturityDate, dailyLiquidity);
    }
}
