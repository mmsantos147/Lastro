package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.FixedIncomeTerms;
import io.matheusms.lastro.domain.port.FixedIncomeTermsRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class FixedIncomeTermsPersistenceAdapter implements FixedIncomeTermsRepository {

    private final FixedIncomeSecurityJpaRepository repository;

    FixedIncomeTermsPersistenceAdapter(FixedIncomeSecurityJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public FixedIncomeTerms save(FixedIncomeTerms terms) {
        return repository.save(FixedIncomeSecurityEntity.from(terms)).toDomain();
    }

    @Override
    public Optional<FixedIncomeTerms> findByAsset(Long assetId) {
        return repository.findById(assetId).map(FixedIncomeSecurityEntity::toDomain);
    }

    @Override
    public List<FixedIncomeTerms> findAll() {
        return repository.findAll().stream().map(FixedIncomeSecurityEntity::toDomain).toList();
    }
}
