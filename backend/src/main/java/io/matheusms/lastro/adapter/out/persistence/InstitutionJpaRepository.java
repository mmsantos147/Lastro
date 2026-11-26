package io.matheusms.lastro.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface InstitutionJpaRepository extends JpaRepository<InstitutionEntity, Long> {

    Optional<InstitutionEntity> findByCnpj(String cnpj);

    List<InstitutionEntity> findAllByOrderByNameAsc();
}
