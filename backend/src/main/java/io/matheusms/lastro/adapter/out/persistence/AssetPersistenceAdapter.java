package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.Asset;
import io.matheusms.lastro.domain.port.AssetRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AssetPersistenceAdapter implements AssetRepository {

    private final AssetJpaRepository repository;

    AssetPersistenceAdapter(AssetJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Asset save(Asset asset) {
        return repository.save(AssetEntity.from(asset)).toDomain();
    }

    @Override
    public Optional<Asset> findById(Long id) {
        return repository.findById(id).map(AssetEntity::toDomain);
    }

    @Override
    public Optional<Asset> findByCode(String code) {
        // Codes are stored in upper case (see Asset)
        return repository.findByCode(code.strip().toUpperCase()).map(AssetEntity::toDomain);
    }

    @Override
    public List<Asset> findAll() {
        return repository.findAllByOrderByCodeAsc().stream().map(AssetEntity::toDomain).toList();
    }
}
