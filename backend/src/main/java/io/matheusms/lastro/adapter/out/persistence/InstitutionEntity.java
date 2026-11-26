package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.Cnpj;
import io.matheusms.lastro.domain.model.Institution;
import io.matheusms.lastro.domain.model.InstitutionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "institution")
class InstitutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InstitutionType type;

    @Column(unique = true, length = 14)
    private String cnpj;

    protected InstitutionEntity() {
    }

    static InstitutionEntity from(Institution institution) {
        var entity = new InstitutionEntity();
        entity.id = institution.id();
        entity.name = institution.name();
        entity.type = institution.type();
        entity.cnpj = institution.cnpj() == null ? null : institution.cnpj().value();
        return entity;
    }

    Institution toDomain() {
        return new Institution(id, name, type, cnpj == null ? null : new Cnpj(cnpj));
    }
}
