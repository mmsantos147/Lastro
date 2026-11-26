package io.matheusms.lastro.domain.port;

import io.matheusms.lastro.domain.model.Analysis;
import java.util.List;
import java.util.Optional;

public interface AnalysisRepository {

    Analysis save(Analysis analysis);

    Optional<Analysis> findById(Long id);

    /** Analyses of the user, newest first. */
    List<Analysis> findByUser(Long userId);

    void deleteById(Long id);
}
