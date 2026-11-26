package io.matheusms.lastro.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface AnalysisJpaRepository extends JpaRepository<AnalysisEntity, Long> {

    List<AnalysisEntity> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
