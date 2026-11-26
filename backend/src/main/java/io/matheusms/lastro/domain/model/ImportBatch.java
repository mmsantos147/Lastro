package io.matheusms.lastro.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * One imported file. The records it created point back to it, so an import can be undone.
 *
 * @param id {@code null} until persisted
 * @param institutionId where the imported trades and distributions happened (chosen by the user)
 * @param fileHash SHA-256 of the file, in hex
 * @param processedRows records created by this file
 * @param skippedRows rows already imported before, ignored or rejected
 */
public record ImportBatch(
        Long id,
        Long userId,
        Long institutionId,
        EntrySource source,
        String fileName,
        String fileHash,
        Instant importedAt,
        ImportStatus status,
        int processedRows,
        int skippedRows) {

    public ImportBatch {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(institutionId, "institutionId must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(fileName, "fileName must not be null");
        Objects.requireNonNull(fileHash, "fileHash must not be null");
        Objects.requireNonNull(importedAt, "importedAt must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (source != EntrySource.B3_IMPORT && source != EntrySource.BINANCE_CSV) {
            throw new IllegalArgumentException("Not a file source: " + source);
        }
        if (processedRows < 0 || skippedRows < 0) {
            throw new IllegalArgumentException("Row counts must not be negative");
        }
    }
}
