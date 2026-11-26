package io.matheusms.lastro.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface CorporateActionJpaRepository extends JpaRepository<CorporateActionEntity, Long> {

    List<CorporateActionEntity> findByAssetIdOrderByRecordDateAscIdAsc(Long assetId);

    List<CorporateActionEntity> findAllByOrderByRecordDateAscIdAsc();
}
