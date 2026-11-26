package io.matheusms.lastro.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import io.matheusms.lastro.domain.port.AssetRepository;
import io.matheusms.lastro.domain.port.CorporateActionRepository;
import io.matheusms.lastro.domain.port.DistributionRepository;
import io.matheusms.lastro.domain.port.EconomicIndicatorRepository;
import io.matheusms.lastro.domain.port.QuoteRepository;
import io.matheusms.lastro.domain.port.InstitutionRepository;
import io.matheusms.lastro.domain.port.TradeRepository;
import io.matheusms.lastro.domain.port.TransferRepository;
import io.matheusms.lastro.domain.service.InsufficientQuantityException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TradeServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ASSET_ID = 2L;
    private static final Long BROKER_ID = 3L;
    private static final LocalDate DAY_1 = LocalDate.of(2026, 3, 2);
    private static final LocalDate DAY_2 = LocalDate.of(2026, 3, 3);

    @Mock
    private AssetRepository assets;
    @Mock
    private InstitutionRepository institutions;
    @Mock
    private TradeRepository trades;
    @Mock
    private TransferRepository transfers;
    @Mock
    private CorporateActionRepository corporateActions;
    @Mock
    private DistributionRepository distributions;
    @Mock
    private QuoteRepository quotes;
    @Mock
    private EconomicIndicatorRepository indicators;

    private TradeService service;

    @BeforeEach
    void setUp() {
        var loader = new PortfolioLoader(trades, transfers, corporateActions, distributions, quotes, indicators);
        var positions = new PositionService(assets, loader, Clock.systemUTC());
        service = new TradeService(trades, new EntryValidator(assets, institutions, loader, positions));
        given(trades.findTradedAssetIds(USER_ID)).willReturn(Set.of(ASSET_ID));
        given(distributions.findByUser(USER_ID)).willReturn(List.of());
        given(assets.findById(ASSET_ID)).willReturn(Optional.of(
                new Asset(ASSET_ID, "ITSA4", "Itaúsa", AssetClass.STOCK, null, Currency.BRL, true)));
        given(institutions.findById(BROKER_ID)).willReturn(Optional.of(
                new Institution(BROKER_ID, "Broker", InstitutionType.EXCHANGE, null)));
        given(transfers.findByUserAndAsset(USER_ID, ASSET_ID)).willReturn(List.of());
        given(corporateActions.findByAsset(ASSET_ID)).willReturn(List.of());
        given(trades.save(any())).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldRegisterSellWithinThePosition() {
        given(trades.findByUserAndAsset(USER_ID, ASSET_ID)).willReturn(List.of(trade(10L, TradeType.BUY, DAY_1, "10")));

        var sell = trade(null, TradeType.SELL, DAY_2, "10");

        assertThat(service.register(sell)).isEqualTo(sell);
    }

    @Test
    void shouldRejectSellExceedingThePositionAtThatDate() {
        given(trades.findByUserAndAsset(USER_ID, ASSET_ID)).willReturn(List.of(trade(10L, TradeType.BUY, DAY_2, "10")));

        assertThatThrownBy(() -> service.register(trade(null, TradeType.SELL, DAY_1, "5")))
                .isInstanceOf(InsufficientQuantityException.class);
        verify(trades, never()).save(any());
    }

    @Test
    void shouldRejectTradeOfUnknownAsset() {
        var trade = new Trade(null, USER_ID, 99L, BROKER_ID, TradeType.BUY, DAY_1, Quantity.of(1),
                Money.brl("10"), TradeFee.none(Currency.BRL), EntrySource.MANUAL, null);

        assertThatThrownBy(() -> service.register(trade)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldRejectDeletingABuyThatALaterSellDependsOn() {
        given(trades.findByUserAndAsset(USER_ID, ASSET_ID)).willReturn(List.of(
                trade(10L, TradeType.BUY, DAY_1, "10"),
                trade(11L, TradeType.SELL, DAY_2, "10")));
        given(trades.findById(10L)).willReturn(Optional.of(trade(10L, TradeType.BUY, DAY_1, "10")));

        assertThatThrownBy(() -> service.delete(USER_ID, 10L)).isInstanceOf(InsufficientQuantityException.class);
        verify(trades, never()).deleteById(any());
    }

    @Test
    void shouldRejectFeePaidInAnAssetThatIsNotHeld() {
        var bnbId = 9L;
        given(assets.findById(bnbId)).willReturn(Optional.of(
                new Asset(bnbId, "BNB", "BNB", AssetClass.CRYPTO, null, Currency.USD, true)));
        given(trades.findByUserAndAsset(USER_ID, ASSET_ID)).willReturn(List.of());
        var trade = new Trade(null, USER_ID, ASSET_ID, BROKER_ID, TradeType.BUY, DAY_1, Quantity.of(1),
                Money.of("100", Currency.USD), new TradeFee.InAsset(bnbId, Quantity.of("0.01")), EntrySource.MANUAL, null);

        assertThatThrownBy(() -> service.register(trade)).isInstanceOf(InsufficientQuantityException.class);
    }

    @Test
    void shouldNotDeleteTradesOfAnotherUser() {
        var othersTrade = new Trade(10L, 42L, ASSET_ID, BROKER_ID, TradeType.BUY, DAY_1, Quantity.of(1),
                Money.brl("10"), TradeFee.none(Currency.BRL), EntrySource.MANUAL, null);
        given(trades.findById(10L)).willReturn(Optional.of(othersTrade));

        assertThatThrownBy(() -> service.delete(USER_ID, 10L)).isInstanceOf(NotFoundException.class);
        verify(trades, never()).deleteById(any());
    }

    private static Trade trade(Long id, TradeType type, LocalDate date, String quantity) {
        return new Trade(id, USER_ID, ASSET_ID, BROKER_ID, type, date, Quantity.of(quantity), Money.brl("10"),
                TradeFee.none(Currency.BRL), EntrySource.MANUAL, null);
    }
}
