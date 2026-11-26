package io.matheusms.lastro.application;

import io.matheusms.lastro.domain.model.Institution;
import io.matheusms.lastro.domain.port.InstitutionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Places where assets are held. Shared by all users. */
@Service
public class InstitutionService {

    private final InstitutionRepository institutions;

    InstitutionService(InstitutionRepository institutions) {
        this.institutions = institutions;
    }

    @Transactional(readOnly = true)
    public List<Institution> list() {
        return institutions.findAll();
    }

    @Transactional
    public Institution create(Institution institution) {
        if (institution.cnpj() != null && institutions.findByCnpj(institution.cnpj()).isPresent()) {
            throw new ConflictException("An institution with CNPJ %s already exists".formatted(institution.cnpj()));
        }
        return institutions.save(institution);
    }
}
