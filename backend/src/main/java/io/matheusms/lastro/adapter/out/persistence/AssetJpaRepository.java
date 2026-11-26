package io.matheusms.lastro.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface AssetJpaRepository extends JpaRepository<AssetEntity, Long> {

    Optional<AssetEntity> findByCode(String code);

    List<AssetEntity> findAllByOrderByCodeAsc();
}
