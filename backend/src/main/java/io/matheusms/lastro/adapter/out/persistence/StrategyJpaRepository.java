package io.matheusms.lastro.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface StrategyJpaRepository extends JpaRepository<StrategyEntity, Long> {

    List<StrategyEntity> findByUserIdOrderByNameAsc(Long userId);

    Optional<StrategyEntity> findByUserIdAndActiveTrue(Long userId);
}
