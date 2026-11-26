package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.Transfer;
import io.matheusms.lastro.domain.port.TransferRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TransferPersistenceAdapter implements TransferRepository {

    private final TransferJpaRepository repository;

    TransferPersistenceAdapter(TransferJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Transfer save(Transfer transfer) {
        return repository.save(TransferEntity.from(transfer)).toDomain();
    }

    @Override
    public List<Transfer> findByUserAndAsset(Long userId, Long assetId) {
        return repository.findByUserIdAndAssetIdOrderByDateAscIdAsc(userId, assetId).stream()
                .map(TransferEntity::toDomain)
                .toList();
    }

    @Override
    public List<Transfer> findByUser(Long userId) {
        return repository.findByUserIdOrderByDateAscIdAsc(userId).stream().map(TransferEntity::toDomain).toList();
    }

    @Override
    public Optional<Transfer> findById(Long id) {
        return repository.findById(id).map(TransferEntity::toDomain);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
