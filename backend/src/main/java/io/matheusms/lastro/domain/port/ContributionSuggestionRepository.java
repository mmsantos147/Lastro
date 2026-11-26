package io.matheusms.lastro.domain.port;

import io.matheusms.lastro.domain.model.ContributionSuggestion;
import java.util.Optional;

public interface ContributionSuggestionRepository {

    ContributionSuggestion save(ContributionSuggestion suggestion);

    Optional<ContributionSuggestion> findById(Long id);

    boolean existsByStrategy(Long strategyId);
}
