package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.Strategy;
import io.matheusms.lastro.domain.port.StrategyRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StrategyPersistenceAdapter implements StrategyRepository {

    private final StrategyJpaRepository repository;

    StrategyPersistenceAdapter(StrategyJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Strategy save(Strategy strategy) {
        if (strategy.id() == null) {
            return repository.save(StrategyEntity.from(strategy)).toDomain();
        }
        var entity = repository.findById(strategy.id())
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + strategy.id()));
        entity.update(strategy);
        return repository.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<Strategy> findById(Long id) {
        return repository.findById(id).map(StrategyEntity::toDomain);
    }

    @Override
    public List<Strategy> findByUser(Long userId) {
        return repository.findByUserIdOrderByNameAsc(userId).stream().map(StrategyEntity::toDomain).toList();
    }

    @Override
    public Optional<Strategy> findActive(Long userId) {
        return repository.findByUserIdAndActiveTrue(userId).map(StrategyEntity::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
