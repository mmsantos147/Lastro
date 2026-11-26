-- Phase 3/6: file imports (B3 Excel, Binance CSV) and mergers.

-- A merger ("incorporação") moves the position to the successor asset (ADR 0009).
ALTER TABLE corporate_action ADD COLUMN successor_asset_id BIGINT REFERENCES asset (id);
ALTER TABLE corporate_action ADD CONSTRAINT ck_corporate_action_successor CHECK (
    (type = 'MERGER') = (successor_asset_id IS NOT NULL) AND successor_asset_id IS DISTINCT FROM asset_id);

-- One uploaded file.
CREATE TABLE import_batch (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES app_user (id),
    institution_id BIGINT       NOT NULL REFERENCES institution (id),
    source         VARCHAR(20)  NOT NULL CHECK (source IN ('B3_IMPORT', 'BINANCE_CSV')),
    file_name      VARCHAR(255) NOT NULL,
    file_hash      VARCHAR(64)  NOT NULL,
    imported_at    TIMESTAMPTZ  NOT NULL,
    status         VARCHAR(30)  NOT NULL CHECK (status IN ('COMPLETED', 'COMPLETED_WITH_ISSUES')),
    -- Records created by this file
    processed_rows INTEGER      NOT NULL CHECK (processed_rows >= 0),
    -- Already imported, ignored or rejected
    skipped_rows   INTEGER      NOT NULL CHECK (skipped_rows >= 0)
);

CREATE INDEX idx_import_batch_user ON import_batch (user_id, imported_at);

-- Provenance of each imported record. The natural key (a hash of the source row, see ADR 0009) makes re-imports
-- idempotent. Deleting the trade or distribution deletes the link, so a later import brings it back.
CREATE TABLE imported_entry (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES app_user (id),
    import_batch_id BIGINT      NOT NULL REFERENCES import_batch (id) ON DELETE CASCADE,
    natural_key     VARCHAR(64) NOT NULL,
    trade_id        BIGINT REFERENCES trade (id) ON DELETE CASCADE,
    distribution_id BIGINT REFERENCES distribution (id) ON DELETE CASCADE,
    CONSTRAINT ck_imported_entry_target CHECK ((trade_id IS NULL) <> (distribution_id IS NULL))
);

CREATE UNIQUE INDEX uk_imported_entry_natural_key ON imported_entry (user_id, natural_key);
CREATE INDEX idx_imported_entry_batch ON imported_entry (import_batch_id);
