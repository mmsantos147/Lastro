package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.Analysis;
import io.matheusms.lastro.domain.port.AnalysisRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AnalysisPersistenceAdapter implements AnalysisRepository {

    private final AnalysisJpaRepository repository;

    AnalysisPersistenceAdapter(AnalysisJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Analysis save(Analysis analysis) {
        return repository.save(AnalysisEntity.from(analysis)).toDomain();
    }

    @Override
    public Optional<Analysis> findById(Long id) {
        return repository.findById(id).map(AnalysisEntity::toDomain);
    }

    @Override
    public List<Analysis> findByUser(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream().map(AnalysisEntity::toDomain).toList();
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
