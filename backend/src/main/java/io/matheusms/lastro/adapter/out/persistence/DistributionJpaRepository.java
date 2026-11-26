package io.matheusms.lastro.adapter.out.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface DistributionJpaRepository extends JpaRepository<DistributionEntity, Long> {

    List<DistributionEntity> findByUserIdAndPaymentDateBetweenOrderByPaymentDateAscIdAsc(
            Long userId, LocalDate from, LocalDate to);

    List<DistributionEntity> findByUserIdOrderByPaymentDateAscIdAsc(Long userId);
}
