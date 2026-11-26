package io.matheusms.lastro.domain.port;

import io.matheusms.lastro.domain.model.CorporateAction;
import java.util.List;
import java.util.Optional;

public interface CorporateActionRepository {

    CorporateAction save(CorporateAction corporateAction);

    /** Corporate actions of an asset, ordered by record date. */
    List<CorporateAction> findByAsset(Long assetId);

    /** All corporate actions, ordered by record date. */
    List<CorporateAction> findAll();

    Optional<CorporateAction> findById(Long id);

    void deleteById(Long id);
}
