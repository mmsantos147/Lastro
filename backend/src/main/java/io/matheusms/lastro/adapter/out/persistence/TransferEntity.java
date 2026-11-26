package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.EntrySource;
import io.matheusms.lastro.domain.model.Quantity;
import io.matheusms.lastro.domain.model.Transfer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "transfer")
class TransferEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "from_institution_id", nullable = false)
    private Long fromInstitutionId;

    @Column(name = "to_institution_id", nullable = false)
    private Long toInstitutionId;

    @Column(name = "transfer_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal fee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EntrySource source;

    @Column(name = "external_id", length = 100)
    private String externalId;

    protected TransferEntity() {
    }

    static TransferEntity from(Transfer transfer) {
        var entity = new TransferEntity();
        entity.id = transfer.id();
        entity.userId = transfer.userId();
        entity.assetId = transfer.assetId();
        entity.fromInstitutionId = transfer.fromInstitutionId();
        entity.toInstitutionId = transfer.toInstitutionId();
        entity.date = transfer.date();
        entity.quantity = transfer.quantity().value();
        entity.fee = transfer.fee().value();
        entity.source = transfer.source();
        entity.externalId = transfer.externalId();
        return entity;
    }

    Transfer toDomain() {
        return new Transfer(id, userId, assetId, fromInstitutionId, toInstitutionId, date,
                new Quantity(quantity), new Quantity(fee), source, externalId);
    }
}
