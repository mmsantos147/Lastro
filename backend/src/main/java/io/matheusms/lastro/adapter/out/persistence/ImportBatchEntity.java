package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.EntrySource;
import io.matheusms.lastro.domain.model.ImportBatch;
import io.matheusms.lastro.domain.model.ImportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "import_batch")
class ImportBatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "institution_id", nullable = false)
    private Long institutionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EntrySource source;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ImportStatus status;

    @Column(name = "processed_rows", nullable = false)
    private int processedRows;

    @Column(name = "skipped_rows", nullable = false)
    private int skippedRows;

    protected ImportBatchEntity() {
    }

    static ImportBatchEntity from(ImportBatch batch) {
        var entity = new ImportBatchEntity();
        entity.id = batch.id();
        entity.userId = batch.userId();
        entity.institutionId = batch.institutionId();
        entity.source = batch.source();
        entity.fileName = batch.fileName();
        entity.fileHash = batch.fileHash();
        entity.importedAt = batch.importedAt();
        entity.status = batch.status();
        entity.processedRows = batch.processedRows();
        entity.skippedRows = batch.skippedRows();
        return entity;
    }

    ImportBatch toDomain() {
        return new ImportBatch(id, userId, institutionId, source, fileName, fileHash, importedAt, status,
                processedRows, skippedRows);
    }
}
