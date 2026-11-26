package io.matheusms.lastro.domain.port;

import io.matheusms.lastro.domain.model.ImportBatch;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Imported files and the provenance of the records they created. */
public interface ImportBatchRepository {

    ImportBatch save(ImportBatch batch);

    Optional<ImportBatch> findById(Long id);

    /** Batches of the user, newest first. */
    List<ImportBatch> findByUser(Long userId);

    /** Natural keys of every record the user has imported (and not deleted since). */
    Set<String> findNaturalKeys(Long userId);

    void linkTrade(Long userId, Long batchId, String naturalKey, Long tradeId);

    void linkDistribution(Long userId, Long batchId, String naturalKey, Long distributionId);

    List<Long> findTradeIds(Long batchId);

    List<Long> findDistributionIds(Long batchId);

    /** Deletes the batch and its links (not the records). */
    void deleteById(Long id);
}
