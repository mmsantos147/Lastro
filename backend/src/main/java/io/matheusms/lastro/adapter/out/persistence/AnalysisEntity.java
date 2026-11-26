package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.Analysis;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "analysis")
class AnalysisEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(precision = 20, scale = 8)
    private BigDecimal amount;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "contribution_suggestion_id")
    private Long contributionSuggestionId;

    protected AnalysisEntity() {
    }

    static AnalysisEntity from(Analysis analysis) {
        var entity = new AnalysisEntity();
        entity.id = analysis.id();
        entity.userId = analysis.userId();
        entity.createdAt = analysis.createdAt();
        entity.title = analysis.title();
        entity.amount = analysis.amount() == null ? null : analysis.amount().amount();
        entity.text = analysis.text();
        entity.contributionSuggestionId = analysis.contributionSuggestionId();
        return entity;
    }

    Analysis toDomain() {
        return new Analysis(id, userId, createdAt, title, amount == null ? null : new Money(amount, Currency.BRL), text,
                contributionSuggestionId);
    }
}
