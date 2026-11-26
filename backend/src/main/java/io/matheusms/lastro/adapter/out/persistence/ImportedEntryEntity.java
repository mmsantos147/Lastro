package io.matheusms.lastro.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Link between an imported record (trade or distribution) and the batch and source row it came from. */
@Entity
@Table(name = "imported_entry")
class ImportedEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "import_batch_id", nullable = false)
    private Long importBatchId;

    @Column(name = "natural_key", nullable = false, length = 64)
    private String naturalKey;

    @Column(name = "trade_id")
    private Long tradeId;

    @Column(name = "distribution_id")
    private Long distributionId;

    protected ImportedEntryEntity() {
    }

    ImportedEntryEntity(Long userId, Long importBatchId, String naturalKey, Long tradeId, Long distributionId) {
        this.userId = userId;
        this.importBatchId = importBatchId;
        this.naturalKey = naturalKey;
        this.tradeId = tradeId;
        this.distributionId = distributionId;
    }
}
