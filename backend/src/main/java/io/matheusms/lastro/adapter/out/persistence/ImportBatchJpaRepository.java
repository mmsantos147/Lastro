package io.matheusms.lastro.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ImportBatchJpaRepository extends JpaRepository<ImportBatchEntity, Long> {

    List<ImportBatchEntity> findByUserIdOrderByImportedAtDescIdDesc(Long userId);
}
