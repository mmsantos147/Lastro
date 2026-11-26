package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.ContributionSuggestion;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.SuggestionSource;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "contribution_suggestion")
class ContributionSuggestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "strategy_id", nullable = false)
    private Long strategyId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private SuggestionSource source;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "suggestion_id", nullable = false)
    @OrderBy("position")
    private List<SuggestionItemEntity> items = new ArrayList<>();

    protected ContributionSuggestionEntity() {
    }

    static ContributionSuggestionEntity from(ContributionSuggestion suggestion) {
        var entity = new ContributionSuggestionEntity();
        entity.id = suggestion.id();
        entity.userId = suggestion.userId();
        entity.strategyId = suggestion.strategyId();
        entity.createdAt = suggestion.createdAt();
        entity.amount = suggestion.amount().amount();
        entity.source = suggestion.source();
        for (int i = 0; i < suggestion.items().size(); i++) {
            entity.items.add(SuggestionItemEntity.from(i, suggestion.items().get(i)));
        }
        return entity;
    }

    ContributionSuggestion toDomain() {
        return new ContributionSuggestion(id, userId, strategyId, createdAt, Money.brl(amount.toPlainString()), source,
                items.stream().map(SuggestionItemEntity::toDomain).toList());
    }
}
