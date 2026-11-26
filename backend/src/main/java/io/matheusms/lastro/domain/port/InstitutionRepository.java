package io.matheusms.lastro.domain.port;

import io.matheusms.lastro.domain.model.Cnpj;
import io.matheusms.lastro.domain.model.Institution;
import java.util.List;
import java.util.Optional;

public interface InstitutionRepository {

    Institution save(Institution institution);

    Optional<Institution> findById(Long id);

    Optional<Institution> findByCnpj(Cnpj cnpj);

    /** All institutions, ordered by name. */
    List<Institution> findAll();
}
