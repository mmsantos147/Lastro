package io.matheusms.lastro.adapter.out.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TradeJpaRepository extends JpaRepository<TradeEntity, Long> {

    List<TradeEntity> findByUserIdAndAssetIdOrderByDateAscIdAsc(Long userId, Long assetId);

    List<TradeEntity> findByUserIdOrderByDateAscIdAsc(Long userId);

    Optional<TradeEntity> findFirstByAssetIdOrderByDateAscIdAsc(Long assetId);

    @Query("select distinct t.assetId from TradeEntity t where t.userId = :userId")
    Set<Long> findDistinctAssetIdsByUserId(Long userId);

    @Query("select min(t.date) from TradeEntity t where t.assetId = :assetId")
    Optional<LocalDate> findFirstDateByAssetId(Long assetId);

    @Query("select min(t.date) from TradeEntity t")
    Optional<LocalDate> findFirstDate();
}
