package io.matheusms.lastro.domain.port;

import io.matheusms.lastro.domain.model.Strategy;
import java.util.List;
import java.util.Optional;

public interface StrategyRepository {

    /** Inserts or replaces the strategy with its targets. */
    Strategy save(Strategy strategy);

    Optional<Strategy> findById(Long id);

    /** Strategies of the user, ordered by name. */
    List<Strategy> findByUser(Long userId);

    Optional<Strategy> findActive(Long userId);

    void deleteById(Long id);
}
