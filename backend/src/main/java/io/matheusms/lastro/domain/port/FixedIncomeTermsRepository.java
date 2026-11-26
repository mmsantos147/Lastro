package io.matheusms.lastro.domain.port;

import io.matheusms.lastro.domain.model.FixedIncomeTerms;
import java.util.List;
import java.util.Optional;

public interface FixedIncomeTermsRepository {

    /** Inserts or replaces the terms of the asset. */
    FixedIncomeTerms save(FixedIncomeTerms terms);

    Optional<FixedIncomeTerms> findByAsset(Long assetId);

    List<FixedIncomeTerms> findAll();
}
