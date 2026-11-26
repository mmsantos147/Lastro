package io.matheusms.lastro.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface TransferJpaRepository extends JpaRepository<TransferEntity, Long> {

    List<TransferEntity> findByUserIdAndAssetIdOrderByDateAscIdAsc(Long userId, Long assetId);

    List<TransferEntity> findByUserIdOrderByDateAscIdAsc(Long userId);
}
