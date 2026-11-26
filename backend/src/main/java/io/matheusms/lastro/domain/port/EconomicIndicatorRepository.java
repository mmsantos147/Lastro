package io.matheusms.lastro.domain.port;

import io.matheusms.lastro.domain.model.EconomicIndicator;
import io.matheusms.lastro.domain.model.IndicatorType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EconomicIndicatorRepository {

    /** Inserts or replaces the value of the same type and date. */
    void upsert(Collection<EconomicIndicator> indicators);

    /** Values between {@code from} and {@code to} (inclusive), ordered by date. */
    List<EconomicIndicator> findByTypeBetween(IndicatorType type, LocalDate from, LocalDate to);

    Optional<LocalDate> findLatestDate(IndicatorType type);

    Optional<LocalDate> findEarliestDate(IndicatorType type);
}
