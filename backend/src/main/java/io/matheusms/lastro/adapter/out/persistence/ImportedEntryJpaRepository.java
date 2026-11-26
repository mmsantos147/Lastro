package io.matheusms.lastro.adapter.out.persistence;

import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ImportedEntryJpaRepository extends JpaRepository<ImportedEntryEntity, Long> {

    @Query("select e.naturalKey from ImportedEntryEntity e where e.userId = :userId")
    Set<String> findNaturalKeysByUserId(Long userId);

    @Query("select e.tradeId from ImportedEntryEntity e where e.importBatchId = :batchId and e.tradeId is not null")
    List<Long> findTradeIdsByBatchId(Long batchId);

    @Query("""
            select e.distributionId from ImportedEntryEntity e
            where e.importBatchId = :batchId and e.distributionId is not null""")
    List<Long> findDistributionIdsByBatchId(Long batchId);

    @Modifying
    @Query("delete from ImportedEntryEntity e where e.importBatchId = :batchId")
    void deleteByBatchId(Long batchId);
}
