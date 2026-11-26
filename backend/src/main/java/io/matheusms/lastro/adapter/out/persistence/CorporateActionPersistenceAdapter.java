package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.CorporateAction;
import io.matheusms.lastro.domain.port.CorporateActionRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CorporateActionPersistenceAdapter implements CorporateActionRepository {

    private final CorporateActionJpaRepository repository;

    CorporateActionPersistenceAdapter(CorporateActionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public CorporateAction save(CorporateAction corporateAction) {
        return repository.save(CorporateActionEntity.from(corporateAction)).toDomain();
    }

    @Override
    public List<CorporateAction> findByAsset(Long assetId) {
        return repository.findByAssetIdOrderByRecordDateAscIdAsc(assetId).stream()
                .map(CorporateActionEntity::toDomain)
                .toList();
    }

    @Override
    public List<CorporateAction> findAll() {
        return repository.findAllByOrderByRecordDateAscIdAsc().stream()
                .map(CorporateActionEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<CorporateAction> findById(Long id) {
        return repository.findById(id).map(CorporateActionEntity::toDomain);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
