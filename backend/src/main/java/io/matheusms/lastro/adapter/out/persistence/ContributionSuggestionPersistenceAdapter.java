package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.ContributionSuggestion;
import io.matheusms.lastro.domain.port.ContributionSuggestionRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ContributionSuggestionPersistenceAdapter implements ContributionSuggestionRepository {

    private final ContributionSuggestionJpaRepository repository;

    ContributionSuggestionPersistenceAdapter(ContributionSuggestionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public ContributionSuggestion save(ContributionSuggestion suggestion) {
        return repository.save(ContributionSuggestionEntity.from(suggestion)).toDomain();
    }

    @Override
    public Optional<ContributionSuggestion> findById(Long id) {
        return repository.findById(id).map(ContributionSuggestionEntity::toDomain);
    }

    @Override
    public boolean existsByStrategy(Long strategyId) {
        return repository.existsByStrategyId(strategyId);
    }
}
