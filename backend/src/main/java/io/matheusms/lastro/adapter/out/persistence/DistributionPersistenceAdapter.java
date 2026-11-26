package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.Distribution;
import io.matheusms.lastro.domain.port.DistributionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DistributionPersistenceAdapter implements DistributionRepository {

    private final DistributionJpaRepository repository;

    DistributionPersistenceAdapter(DistributionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Distribution save(Distribution distribution) {
        return repository.save(DistributionEntity.from(distribution)).toDomain();
    }

    @Override
    public List<Distribution> findByUserAndPaymentDateBetween(Long userId, LocalDate from, LocalDate to) {
        return repository.findByUserIdAndPaymentDateBetweenOrderByPaymentDateAscIdAsc(userId, from, to).stream()
                .map(DistributionEntity::toDomain)
                .toList();
    }

    @Override
    public List<Distribution> findByUser(Long userId) {
        return repository.findByUserIdOrderByPaymentDateAscIdAsc(userId).stream()
                .map(DistributionEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<Distribution> findById(Long id) {
        return repository.findById(id).map(DistributionEntity::toDomain);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
