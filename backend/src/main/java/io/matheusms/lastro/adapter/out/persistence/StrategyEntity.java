package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.Strategy;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "strategy")
class StrategyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean active;

    // The strategy owns its targets: they are replaced as a whole on every save
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "strategy_id", nullable = false)
    @OrderBy("id")
    private List<TargetAllocationEntity> targets = new ArrayList<>();

    protected StrategyEntity() {
    }

    static StrategyEntity from(Strategy strategy) {
        var entity = new StrategyEntity();
        entity.id = strategy.id();
        entity.update(strategy);
        return entity;
    }

    /** Copies the strategy into this managed entity, replacing the targets. */
    void update(Strategy strategy) {
        userId = strategy.userId();
        name = strategy.name();
        active = strategy.active();
        targets.clear();
        strategy.targets().forEach(target -> targets.add(TargetAllocationEntity.from(target)));
    }

    Strategy toDomain() {
        return new Strategy(id, userId, name, active, targets.stream().map(TargetAllocationEntity::toDomain).toList());
    }
}
