package io.matheusms.lastro.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.matheusms.lastro.domain.model.Asset;
import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.EntrySource;
import io.matheusms.lastro.domain.model.Institution;
import io.matheusms.lastro.domain.model.InstitutionType;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quantity;
import io.matheusms.lastro.domain.model.Trade;
import io.matheusms.lastro.domain.model.TradeFee;
import io.matheusms.lastro.domain.model.TradeType;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@PersistenceAdapterTest
class TradePersistenceAdapterIT {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 1, 5);
    private static final LocalDate DAY_2 = LocalDate.of(2026, 1, 6);

    @Autowired
    private TradePersistenceAdapter trades;
    @Autowired
    private UserPersistenceAdapter users;
    @Autowired
    private InstitutionPersistenceAdapter institutions;
    @Autowired
    private AssetPersistenceAdapter assets;

    private Long userId;
    private Long institutionId;
    private Long assetId;

    @BeforeEach
    void setUp() {
        userId = users.create(TestData.user(), TestData.PASSWORD_HASH).id();
        institutionId = institutions.save(TestData.broker()).id();
        assetId = assets.save(TestData.stock("PETR4")).id();
    }

    @Test
    void shouldKeepFullPrecisionOfQuantitiesAndPrices() {
        var treasuryId = assets.save(new Asset(null, "TESOURO-IPCA-2035", "Tesouro IPCA+ 2035",
                AssetClass.TREASURY, null, Currency.BRL, true)).id();
        var trade = new Trade(null, userId, treasuryId, institutionId, TradeType.SELL, DAY_1,
                Quantity.of("0.53"), Money.brl("3456.12345678"), new TradeFee.InCurrency(Money.brl("0.01")),
                EntrySource.B3_IMPORT, null);

        var saved = trades.save(trade);

        assertThat(trades.findByUserAndAsset(userId, treasuryId)).containsExactly(saved);
        assertThat(saved.quantity()).isEqualTo(Quantity.of("0.53"));
        assertThat(saved.unitPrice()).isEqualTo(Money.brl("3456.12345678"));
    }

    @Test
    void shouldRoundTripCryptoTradeWithFeePaidInAnotherAsset() {
        var exchangeId = institutions.save(new Institution(null, "Binance", InstitutionType.EXCHANGE, null)).id();
        var btcId = assets.save(TestData.crypto("BTC")).id();
        var bnbId = assets.save(TestData.crypto("BNB")).id();
        var trade = new Trade(null, userId, btcId, exchangeId, TradeType.BUY, DAY_1, Quantity.of("0.00012345"),
                Money.of("65432.10", Currency.USD), new TradeFee.InAsset(bnbId, Quantity.of("0.00001234")),
                EntrySource.BINANCE_API, "123456789");

        var saved = trades.save(trade);

        assertThat(trades.findByUserAndAsset(userId, btcId)).containsExactly(saved);
        assertThat(saved.fee()).isEqualTo(new TradeFee.InAsset(bnbId, Quantity.of("0.00001234")));
        assertThat(saved.priceCurrency()).isEqualTo(Currency.USD);
    }

    @Test
    void shouldRejectDuplicateExternalIdInTheSameInstitution() {
        trades.save(withExternalId("abc"));

        assertThatThrownBy(() -> trades.save(withExternalId("abc")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldAllowManyTradesWithoutExternalId() {
        trades.save(TestData.buy(userId, assetId, institutionId, DAY_1, "1"));
        trades.save(TestData.buy(userId, assetId, institutionId, DAY_1, "1"));

        assertThat(trades.findByUserAndAsset(userId, assetId)).hasSize(2);
    }

    @Test
    void shouldReturnTradesOrderedByDateThenInsertionOrder() {
        var secondDay = trades.save(TestData.buy(userId, assetId, institutionId, DAY_2, "1"));
        var firstDayA = trades.save(TestData.buy(userId, assetId, institutionId, DAY_1, "2"));
        var firstDayB = trades.save(TestData.buy(userId, assetId, institutionId, DAY_1, "3"));

        assertThat(trades.findByUserAndAsset(userId, assetId)).containsExactly(firstDayA, firstDayB, secondDay);
    }

    @Test
    void shouldReturnOnlyTradesOfTheGivenUserAndAsset() {
        var otherUserId = users.create(TestData.user(), TestData.PASSWORD_HASH).id();
        var otherAssetId = assets.save(TestData.stock("VALE3")).id();
        var expected = trades.save(TestData.buy(userId, assetId, institutionId, DAY_1, "1"));
        trades.save(TestData.buy(otherUserId, assetId, institutionId, DAY_1, "1"));
        trades.save(TestData.buy(userId, otherAssetId, institutionId, DAY_1, "1"));

        assertThat(trades.findByUserAndAsset(userId, assetId)).containsExactly(expected);
    }

    @Test
    void shouldListTradedAssetsAndAllTradesOfTheUser() {
        var otherAssetId = assets.save(TestData.stock("WEGE3")).id();
        var first = trades.save(TestData.buy(userId, assetId, institutionId, DAY_2, "1"));
        var second = trades.save(TestData.buy(userId, otherAssetId, institutionId, DAY_1, "1"));

        assertThat(trades.findTradedAssetIds(userId)).containsExactlyInAnyOrder(assetId, otherAssetId);
        assertThat(trades.findByUser(userId)).containsExactly(second, first);
    }

    @Test
    void shouldFindAndDeleteById() {
        var saved = trades.save(TestData.buy(userId, assetId, institutionId, DAY_1, "1"));

        assertThat(trades.findById(saved.id())).contains(saved);
        trades.deleteById(saved.id());
        assertThat(trades.findById(saved.id())).isEmpty();
    }

    private Trade withExternalId(String externalId) {
        return new Trade(null, userId, assetId, institutionId, TradeType.BUY, DAY_1, Quantity.of(1),
                Money.brl("10"), TradeFee.none(Currency.BRL), EntrySource.B3_IMPORT, externalId);
    }
}
