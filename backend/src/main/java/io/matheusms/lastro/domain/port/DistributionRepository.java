package io.matheusms.lastro.domain.port;

import io.matheusms.lastro.domain.model.Distribution;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DistributionRepository {

    Distribution save(Distribution distribution);

    /** Distributions of a user paid between {@code from} and {@code to} (inclusive), ordered by payment date. */
    List<Distribution> findByUserAndPaymentDateBetween(Long userId, LocalDate from, LocalDate to);

    /** All distributions of a user, ordered by payment date. */
    List<Distribution> findByUser(Long userId);

    Optional<Distribution> findById(Long id);

    void deleteById(Long id);
}
