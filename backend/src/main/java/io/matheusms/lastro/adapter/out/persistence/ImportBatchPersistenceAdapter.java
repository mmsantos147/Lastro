package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.ImportBatch;
import io.matheusms.lastro.domain.port.ImportBatchRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ImportBatchPersistenceAdapter implements ImportBatchRepository {

    private final ImportBatchJpaRepository batches;
    private final ImportedEntryJpaRepository entries;

    ImportBatchPersistenceAdapter(ImportBatchJpaRepository batches, ImportedEntryJpaRepository entries) {
        this.batches = batches;
        this.entries = entries;
    }

    @Override
    public ImportBatch save(ImportBatch batch) {
        return batches.save(ImportBatchEntity.from(batch)).toDomain();
    }

    @Override
    public Optional<ImportBatch> findById(Long id) {
        return batches.findById(id).map(ImportBatchEntity::toDomain);
    }

    @Override
    public List<ImportBatch> findByUser(Long userId) {
        return batches.findByUserIdOrderByImportedAtDescIdDesc(userId).stream()
                .map(ImportBatchEntity::toDomain)
                .toList();
    }

    @Override
    public Set<String> findNaturalKeys(Long userId) {
        return entries.findNaturalKeysByUserId(userId);
    }

    @Override
    public void linkTrade(Long userId, Long batchId, String naturalKey, Long tradeId) {
        entries.save(new ImportedEntryEntity(userId, batchId, naturalKey, tradeId, null));
    }

    @Override
    public void linkDistribution(Long userId, Long batchId, String naturalKey, Long distributionId) {
        entries.save(new ImportedEntryEntity(userId, batchId, naturalKey, null, distributionId));
    }

    @Override
    public List<Long> findTradeIds(Long batchId) {
        return entries.findTradeIdsByBatchId(batchId);
    }

    @Override
    public List<Long> findDistributionIds(Long batchId) {
        return entries.findDistributionIdsByBatchId(batchId);
    }

    @Override
    public void deleteById(Long id) {
        entries.deleteByBatchId(id);
        batches.deleteById(id);
    }
}
