package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.IndicatorType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface EconomicIndicatorJpaRepository extends JpaRepository<EconomicIndicatorEntity, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO economic_indicator (type, indicator_date, value)
            VALUES (:type, :date, :value)
            ON CONFLICT (type, indicator_date) DO UPDATE SET value = EXCLUDED.value
            """, nativeQuery = true)
    void upsert(String type, LocalDate date, BigDecimal value);

    List<EconomicIndicatorEntity> findByTypeAndDateBetweenOrderByDateAsc(IndicatorType type, LocalDate from, LocalDate to);

    @Query("select max(e.date) from EconomicIndicatorEntity e where e.type = :type")
    Optional<LocalDate> findLatestDate(IndicatorType type);

    @Query("select min(e.date) from EconomicIndicatorEntity e where e.type = :type")
    Optional<LocalDate> findEarliestDate(IndicatorType type);
}
