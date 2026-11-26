package io.matheusms.lastro.domain.port;

import io.matheusms.lastro.domain.model.Asset;
import java.util.List;
import java.util.Optional;

public interface AssetRepository {

    Asset save(Asset asset);

    Optional<Asset> findById(Long id);

    /** @param code ticker or ISIN, case-insensitive */
    Optional<Asset> findByCode(String code);

    /** All assets, ordered by code. */
    List<Asset> findAll();
}
