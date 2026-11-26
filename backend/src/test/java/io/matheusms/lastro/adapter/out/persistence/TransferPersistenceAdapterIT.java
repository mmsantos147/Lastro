package io.matheusms.lastro.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.matheusms.lastro.domain.model.EntrySource;
import io.matheusms.lastro.domain.model.Institution;
import io.matheusms.lastro.domain.model.InstitutionType;
import io.matheusms.lastro.domain.model.Quantity;
import io.matheusms.lastro.domain.model.Transfer;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@PersistenceAdapterTest
class TransferPersistenceAdapterIT {

    @Autowired
    private TransferPersistenceAdapter transfers;
    @Autowired
    private UserPersistenceAdapter users;
    @Autowired
    private InstitutionPersistenceAdapter institutions;
    @Autowired
    private AssetPersistenceAdapter assets;

    private Long userId;
    private Long exchangeId;
    private Long walletId;
    private Long btcId;

    @BeforeEach
    void setUp() {
        userId = users.create(TestData.user(), TestData.PASSWORD_HASH).id();
        exchangeId = institutions.save(new Institution(null, "Binance", InstitutionType.EXCHANGE, null)).id();
        walletId = institutions.save(TestData.wallet()).id();
        btcId = assets.save(TestData.crypto("BTC")).id();
    }

    @Test
    void shouldRoundTripTransferWithFee() {
        var saved = transfers.save(transfer(LocalDate.of(2026, 2, 1), "0.5", "0.0002", "withdrawal-1"));

        assertThat(transfers.findByUserAndAsset(userId, btcId)).containsExactly(saved);
        assertThat(saved.receivedQuantity()).isEqualTo(Quantity.of("0.4998"));
    }

    @Test
    void shouldReturnTransfersOrderedByDate() {
        var later = transfers.save(transfer(LocalDate.of(2026, 3, 1), "0.1", "0", null));
        var earlier = transfers.save(transfer(LocalDate.of(2026, 2, 1), "0.2", "0", null));

        assertThat(transfers.findByUserAndAsset(userId, btcId)).containsExactly(earlier, later);
    }

    private Transfer transfer(LocalDate date, String quantity, String fee, String externalId) {
        return new Transfer(null, userId, btcId, exchangeId, walletId, date,
                Quantity.of(quantity), Quantity.of(fee), EntrySource.BINANCE_API, externalId);
    }
}
