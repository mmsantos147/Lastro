package io.matheusms.lastro.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface QuoteJpaRepository extends JpaRepository<QuoteEntity, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO quote (asset_id, quote_date, close_price, currency, source)
            VALUES (:assetId, :date, :closePrice, :currency, :source)
            ON CONFLICT (asset_id, quote_date, currency)
            DO UPDATE SET close_price = EXCLUDED.close_price, source = EXCLUDED.source
            """, nativeQuery = true)
    void upsert(Long assetId, LocalDate date, BigDecimal closePrice, String currency, String source);

    @Query("select q from QuoteEntity q where q.assetId in :assetIds and q.date <= :to order by q.assetId, q.date")
    List<QuoteEntity> findByAssetsUpTo(Collection<Long> assetIds, LocalDate to);

    @Query("select max(q.date) from QuoteEntity q where q.assetId = :assetId")
    Optional<LocalDate> findLatestDate(Long assetId);

    @Query("select min(q.date) from QuoteEntity q where q.assetId = :assetId")
    Optional<LocalDate> findEarliestDate(Long assetId);

    List<QuoteEntity> findByAssetIdOrderByDateAsc(Long assetId);

    @Modifying
    @Query("delete from QuoteEntity q where q.assetId = :assetId and q.date = :date")
    void deleteByAssetIdAndDate(Long assetId, LocalDate date);
}
