package io.matheusms.lastro.domain.port;

import io.matheusms.lastro.domain.model.Transfer;
import java.util.List;
import java.util.Optional;

public interface TransferRepository {

    Transfer save(Transfer transfer);

    /** Transfers of a user in an asset, ordered by date and then by insertion order. */
    List<Transfer> findByUserAndAsset(Long userId, Long assetId);

    /** All transfers of a user, ordered by date and then by insertion order. */
    List<Transfer> findByUser(Long userId);

    Optional<Transfer> findById(Long id);

    void deleteById(Long id);
}
