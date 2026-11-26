package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.Cnpj;
import io.matheusms.lastro.domain.model.Institution;
import io.matheusms.lastro.domain.port.InstitutionRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class InstitutionPersistenceAdapter implements InstitutionRepository {

    private final InstitutionJpaRepository repository;

    InstitutionPersistenceAdapter(InstitutionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Institution save(Institution institution) {
        return repository.save(InstitutionEntity.from(institution)).toDomain();
    }

    @Override
    public Optional<Institution> findById(Long id) {
        return repository.findById(id).map(InstitutionEntity::toDomain);
    }

    @Override
    public Optional<Institution> findByCnpj(Cnpj cnpj) {
        return repository.findByCnpj(cnpj.value()).map(InstitutionEntity::toDomain);
    }

    @Override
    public List<Institution> findAll() {
        return repository.findAllByOrderByNameAsc().stream().map(InstitutionEntity::toDomain).toList();
    }
}
