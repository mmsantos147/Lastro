package io.matheusms.lastro.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.matheusms.lastro.domain.model.CorporateAction;
import io.matheusms.lastro.domain.model.CorporateActionType;
import io.matheusms.lastro.domain.model.EntrySource;
import io.matheusms.lastro.domain.model.ImportBatch;
import io.matheusms.lastro.domain.model.ImportStatus;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@PersistenceAdapterTest
class ImportBatchPersistenceAdapterIT {

    private static final LocalDate DAY = LocalDate.of(2026, 1, 5);

    @Autowired
    private ImportBatchPersistenceAdapter batches;
    @Autowired
    private TradePersistenceAdapter trades;
    @Autowired
    private UserPersistenceAdapter users;
    @Autowired
    private InstitutionPersistenceAdapter institutions;
    @Autowired
    private AssetPersistenceAdapter assets;
    @Autowired
    private CorporateActionPersistenceAdapter corporateActions;
    @Autowired
    private EntityManager entityManager;

    private Long userId;
    private Long institutionId;
    private Long assetId;

    @BeforeEach
    void setUp() {
        userId = users.create(TestData.user(), TestData.PASSWORD_HASH).id();
        institutionId = institutions.save(TestData.broker()).id();
        assetId = assets.save(TestData.stock("MGLU3")).id();
    }

    @Test
    void shouldKeepTheProvenanceOfImportedTrades() {
        var batch = batches.save(batch());
        var trade = trades.save(TestData.buy(userId, assetId, institutionId, DAY, "100"));

        batches.linkTrade(userId, batch.id(), "key-1", trade.id());

        assertThat(batches.findNaturalKeys(userId)).containsExactly("key-1");
        assertThat(batches.findTradeIds(batch.id())).containsExactly(trade.id());
        assertThat(batches.findByUser(userId)).containsExactly(batch);
    }

    @Test
    void shouldRejectTheSameNaturalKeyTwiceForAUser() {
        var batch = batches.save(batch());
        var first = trades.save(TestData.buy(userId, assetId, institutionId, DAY, "100"));
        var second = trades.save(TestData.buy(userId, assetId, institutionId, DAY, "100"));
        batches.linkTrade(userId, batch.id(), "key-1", first.id());

        assertThatThrownBy(() -> {
            batches.linkTrade(userId, batch.id(), "key-1", second.id());
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldForgetTheKeyWhenTheTradeIsDeleted() {
        var batch = batches.save(batch());
        var trade = trades.save(TestData.buy(userId, assetId, institutionId, DAY, "100"));
        batches.linkTrade(userId, batch.id(), "key-1", trade.id());
        entityManager.flush();

        trades.deleteById(trade.id());
        entityManager.flush();
        entityManager.clear();

        assertThat(batches.findNaturalKeys(userId)).isEmpty();
    }

    @Test
    void shouldStoreTheSuccessorOfAMerger() {
        var successorId = assets.save(TestData.stock("AUAU3")).id();

        var merger = corporateActions.save(new CorporateAction(null, assetId, CorporateActionType.MERGER, DAY,
                BigDecimal.ONE, null, successorId));

        assertThat(corporateActions.findByAsset(assetId)).containsExactly(merger);
        assertThat(merger.successorAssetId()).isEqualTo(successorId);
    }

    private ImportBatch batch() {
        return new ImportBatch(null, userId, institutionId, EntrySource.B3_IMPORT, "movimentacao.xlsx", "a".repeat(64),
                Instant.parse("2026-11-24T12:00:00Z"), ImportStatus.COMPLETED, 1, 0);
    }
}
