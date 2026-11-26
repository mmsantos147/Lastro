package io.matheusms.lastro.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.matheusms.lastro.domain.model.Asset;
import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.Cnpj;
import io.matheusms.lastro.domain.model.CorporateAction;
import io.matheusms.lastro.domain.model.CorporateActionType;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Distribution;
import io.matheusms.lastro.domain.model.DistributionType;
import io.matheusms.lastro.domain.model.EntrySource;
import io.matheusms.lastro.domain.model.ImportBatch;
import io.matheusms.lastro.domain.model.ImportStatus;
import io.matheusms.lastro.domain.model.Institution;
import io.matheusms.lastro.domain.model.InstitutionType;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quantity;
import io.matheusms.lastro.domain.model.Trade;
import io.matheusms.lastro.domain.model.TradeFee;
import io.matheusms.lastro.domain.model.TradeType;
import io.matheusms.lastro.domain.model.statement.AssetReference;
import io.matheusms.lastro.domain.model.statement.ImportIssue;
import io.matheusms.lastro.domain.model.statement.Statement;
import io.matheusms.lastro.domain.model.statement.StatementEntry;
import io.matheusms.lastro.domain.model.statement.TradeLeg;
import io.matheusms.lastro.domain.port.AssetRepository;
import io.matheusms.lastro.domain.port.CorporateActionRepository;
import io.matheusms.lastro.domain.port.DistributionRepository;
import io.matheusms.lastro.domain.port.EconomicIndicatorRepository;
import io.matheusms.lastro.domain.port.FixedIncomeTermsRepository;
import io.matheusms.lastro.domain.port.ImportBatchRepository;
import io.matheusms.lastro.domain.port.InstitutionRepository;
import io.matheusms.lastro.domain.port.QuoteRepository;
import io.matheusms.lastro.domain.port.StatementReader;
import io.matheusms.lastro.domain.port.TradeRepository;
import io.matheusms.lastro.domain.port.TransferRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long BROKER_ID = 3L;
    private static final Long EXCHANGE_ID = 4L;
    private static final LocalDate DAY_1 = LocalDate.of(2025, 3, 10);
    private static final LocalDate DAY_2 = LocalDate.of(2025, 6, 10);
    private static final AssetReference MGLU3 =
            new AssetReference("MGLU3", "Magazine Luiza", AssetClass.STOCK, Currency.BRL);
    private static final AssetReference BTC = new AssetReference("BTC", "BTC", AssetClass.CRYPTO, Currency.USD);
    private static final AssetReference USDT = new AssetReference("USDT", "USDT", AssetClass.CRYPTO, Currency.USD);

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
    @Mock
    private ImportBatchRepository batches;
    @Mock
    private FixedIncomeTermsRepository fixedIncomeTerms;

    private final StubReader b3 = new StubReader(EntrySource.B3_IMPORT);
    private final StubReader binance = new StubReader(EntrySource.BINANCE_CSV);
    private final Map<String, Asset> catalog = new HashMap<>();
    private final Set<String> importedKeys = new HashSet<>();
    private final AtomicLong ids = new AtomicLong(100);
    private ImportService service;

    @BeforeEach
    void setUp() {
        var loader = new PortfolioLoader(trades, transfers, corporateActions, distributions, quotes, indicators);
        var positions = new PositionService(assets, loader, Clock.systemUTC());
        service = new ImportService(List.of(b3, binance), assets, institutions, trades, distributions,
                corporateActions, batches, fixedIncomeTerms, loader, positions,
                new EntryValidator(assets, institutions, loader, positions),
                Clock.fixed(Instant.parse("2026-11-24T12:00:00Z"), ZoneOffset.UTC));

        given(institutions.findById(BROKER_ID)).willReturn(Optional.of(new Institution(BROKER_ID, "Broker",
                InstitutionType.BROKER, new Cnpj("11222333000181"))));
        given(institutions.findById(EXCHANGE_ID)).willReturn(Optional.of(
                new Institution(EXCHANGE_ID, "Binance", InstitutionType.EXCHANGE, null)));
        given(trades.findTradedAssetIds(USER_ID)).willReturn(Set.of());
        given(trades.findByUserAndAsset(anyLong(), anyLong())).willReturn(List.of());
        given(transfers.findByUserAndAsset(anyLong(), anyLong())).willReturn(List.of());
        given(corporateActions.findByAsset(anyLong())).willReturn(List.of());
        given(distributions.findByUser(USER_ID)).willReturn(List.of());
        given(assets.findByCode(anyString())).willAnswer(call -> Optional.ofNullable(catalog.get(call.<String>getArgument(0))));
        given(assets.save(any())).willAnswer(call -> {
            Asset asset = call.getArgument(0);
            var saved = new Asset(ids.incrementAndGet(), asset.code(), asset.name(), asset.assetClass(), asset.cnpj(),
                    asset.quoteCurrency(), asset.active());
            catalog.put(saved.code(), saved);
            return saved;
        });
        given(trades.save(any())).willAnswer(call -> withId(call.<Trade>getArgument(0)));
        given(distributions.save(any())).willAnswer(call -> call.getArgument(0));
        given(corporateActions.save(any())).willAnswer(call -> call.getArgument(0));
        given(batches.findNaturalKeys(USER_ID)).willAnswer(call -> Set.copyOf(importedKeys));
        given(batches.save(any())).willAnswer(call -> {
            ImportBatch batch = call.getArgument(0);
            return new ImportBatch(50L, batch.userId(), batch.institutionId(), batch.source(), batch.fileName(),
                    batch.fileHash(), batch.importedAt(), batch.status(), batch.processedRows(), batch.skippedRows());
        });
    }

    @Test
    void shouldRecordTradesAndDistributionsCreatingMissingAssets() {
        b3.returns(buy(2, DAY_1, MGLU3, "100", "6.86"), dividend(3, DAY_2, MGLU3, "100", "3.05"));

        var result = importB3();

        assertThat(result.trades()).isEqualTo(1);
        assertThat(result.distributions()).isEqualTo(1);
        assertThat(result.batch().status()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(result.batch().processedRows()).isEqualTo(2);
        assertThat(catalog).containsKey("MGLU3");
        var trade = ArgumentCaptor.forClass(Trade.class);
        verify(trades).save(trade.capture());
        assertThat(trade.getValue().source()).isEqualTo(EntrySource.B3_IMPORT);
        assertThat(trade.getValue().institutionId()).isEqualTo(BROKER_ID);
        verify(batches).linkTrade(eq(USER_ID), eq(50L), anyString(), anyLong());
        var distribution = ArgumentCaptor.forClass(Distribution.class);
        verify(distributions).save(distribution.capture());
        assertThat(distribution.getValue().netAmount()).isEqualTo(Money.brl("3.05"));
        assertThat(distribution.getValue().recordDate()).isEqualTo(DAY_2);
    }

    @Test
    void shouldSkipRowsAlreadyImported() {
        b3.returns(buy(2, DAY_1, MGLU3, "100", "6.86"));
        var keys = ArgumentCaptor.forClass(String.class);
        importB3();
        verify(batches).linkTrade(eq(USER_ID), eq(50L), keys.capture(), anyLong());
        importedKeys.add(keys.getValue());

        var second = importB3();

        assertThat(second.trades()).isZero();
        assertThat(second.duplicates()).isEqualTo(1);
    }

    @Test
    void shouldReportRowsThatBreakTheHistoryAndKeepTheOthers() {
        b3.returns(sell(2, DAY_1, MGLU3, "10", "7.00"), dividend(3, DAY_2, MGLU3, "10", "1.00"));

        var result = importB3();

        assertThat(result.trades()).isZero();
        assertThat(result.distributions()).isEqualTo(1);
        assertThat(result.issues()).extracting(ImportIssue::code)
                .containsExactly(ImportIssue.Code.INCONSISTENT_HISTORY);
        assertThat(result.batch().status()).isEqualTo(ImportStatus.COMPLETED_WITH_ISSUES);
    }

    @Test
    void shouldDeriveTheReverseSplitFactorFromTheQuantityHeld() {
        b3.returns(buy(2, DAY_1, MGLU3, "100", "6.86"), corporateAction(3, DAY_2, CorporateActionType.REVERSE_SPLIT, "10"),
                sell(4, DAY_2.plusDays(1), MGLU3, "10", "9.00"));

        var result = importB3();

        assertThat(result.corporateActions()).isEqualTo(1);
        assertThat(result.trades()).isEqualTo(2);
        var action = ArgumentCaptor.forClass(CorporateAction.class);
        verify(corporateActions).save(action.capture());
        assertThat(action.getValue().factor()).isEqualByComparingTo("10");
    }

    @Test
    void shouldDeriveTheSplitFactorFromTheSharesReceived() {
        b3.returns(buy(2, DAY_1, MGLU3, "8", "70.00"), corporateAction(3, DAY_2, CorporateActionType.STOCK_SPLIT, "56"));

        importB3();

        var action = ArgumentCaptor.forClass(CorporateAction.class);
        verify(corporateActions).save(action.capture());
        assertThat(action.getValue().factor()).isEqualByComparingTo("8");
    }

    @Test
    void shouldReportBonusSharesWithUnknownAssignedCost() {
        b3.returns(buy(2, DAY_1, MGLU3, "10", "7.00"), corporateAction(3, DAY_2, CorporateActionType.BONUS_SHARES, "0.5"));

        var result = importB3();

        assertThat(result.corporateActions()).isEqualTo(1);
        assertThat(result.issues()).extracting(ImportIssue::code)
                .containsExactly(ImportIssue.Code.UNKNOWN_ASSIGNED_COST);
    }

    @Test
    void shouldReportEventsOnAssetsNotHeld() {
        b3.returns(corporateAction(3, DAY_2, CorporateActionType.REVERSE_SPLIT, "10"));

        var result = importB3();

        assertThat(result.corporateActions()).isZero();
        assertThat(result.issues()).extracting(ImportIssue::code)
                .containsExactly(ImportIssue.Code.NO_POSITION_FOR_EVENT);
        verify(corporateActions, never()).save(any());
    }

    @Test
    void shouldRecordAConversionAllOrNothing() {
        var buyUsdt = new StatementEntry.TradeGroup(2, DAY_1, "usdt", "buy USDT", List.of(
                new TradeLeg(USDT, TradeType.BUY, Quantity.of("65.2"), Money.brl("6.133"),
                        new TradeLeg.LegFee.InAsset(USDT, Quantity.of("0.0652")))));
        var buyBtc = new StatementEntry.TradeGroup(3, DAY_1, "btc", "buy BTC", List.of(
                new TradeLeg(BTC, TradeType.BUY, Quantity.of("0.00016"), Money.of("96823.80", Currency.USD),
                        TradeLeg.LegFee.NONE),
                new TradeLeg(USDT, TradeType.SELL, Quantity.of("15.4918"), Money.of("1", Currency.USD),
                        TradeLeg.LegFee.NONE)));
        var tooMuch = new StatementEntry.TradeGroup(4, DAY_2, "eth", "buy ETH", List.of(
                new TradeLeg(new AssetReference("ETH", "ETH", AssetClass.CRYPTO, Currency.USD), TradeType.BUY,
                        Quantity.of("1"), Money.of("3000", Currency.USD), TradeLeg.LegFee.NONE),
                new TradeLeg(USDT, TradeType.SELL, Quantity.of("3000"), Money.of("1", Currency.USD),
                        TradeLeg.LegFee.NONE)));
        binance.returns(buyUsdt, buyBtc, tooMuch);

        var result = service.importFile(USER_ID, EntrySource.BINANCE_CSV, EXCHANGE_ID, "binance.csv", new byte[] {1});

        assertThat(result.trades()).isEqualTo(3);
        assertThat(result.issues()).extracting(ImportIssue::line).containsExactly(4);
        var saved = ArgumentCaptor.forClass(Trade.class);
        verify(trades, org.mockito.Mockito.times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Trade::assetId).doesNotContain(catalog.get("ETH").id());
    }

    @Test
    void shouldAskOnceForTheTermsOfANewFixedIncomeSecurity() {
        var cdb = new AssetReference("CDB6255F9RH", "CDB Banco (CDB6255F9RH)", AssetClass.FIXED_INCOME, Currency.BRL);
        b3.returns(buy(2, DAY_1, cdb, "10000", "0.01"), sell(3, DAY_2, cdb, "4502", "0.0113834"));
        given(fixedIncomeTerms.findByAsset(anyLong())).willReturn(Optional.empty());

        var result = importB3();

        assertThat(result.trades()).isEqualTo(2);
        assertThat(result.issues()).extracting(ImportIssue::code)
                .containsExactly(ImportIssue.Code.FIXED_INCOME_TERMS_REQUIRED);
    }

    @Test
    void shouldRejectAnInstitutionOfTheWrongType() {
        assertThatThrownBy(() -> service.importFile(USER_ID, EntrySource.BINANCE_CSV, BROKER_ID, "binance.csv",
                new byte[] {1})).isInstanceOf(IllegalArgumentException.class);
    }

    private ImportService.ImportResult importB3() {
        return service.importFile(USER_ID, EntrySource.B3_IMPORT, BROKER_ID, "b3.xlsx", new byte[] {1});
    }

    private Trade withId(Trade trade) {
        return new Trade(ids.incrementAndGet(), trade.userId(), trade.assetId(), trade.institutionId(), trade.type(),
                trade.date(), trade.quantity(), trade.unitPrice(), trade.fee(), trade.source(), trade.externalId());
    }

    private static StatementEntry buy(int line, LocalDate date, AssetReference asset, String quantity, String price) {
        return trade(line, date, asset, TradeType.BUY, quantity, price);
    }

    private static StatementEntry sell(int line, LocalDate date, AssetReference asset, String quantity, String price) {
        return trade(line, date, asset, TradeType.SELL, quantity, price);
    }

    private static StatementEntry trade(int line, LocalDate date, AssetReference asset, TradeType type,
            String quantity, String price) {
        return new StatementEntry.TradeGroup(line, date, "row-" + line, "row " + line, List.of(new TradeLeg(asset,
                type, Quantity.of(quantity), Money.brl(price), TradeLeg.LegFee.NONE)));
    }

    private static StatementEntry dividend(int line, LocalDate date, AssetReference asset, String quantity,
            String amount) {
        return new StatementEntry.DistributionEntry(line, date, "row-" + line, "row " + line, asset,
                DistributionType.DIVIDEND, Quantity.of(quantity), Money.brl(amount));
    }

    private static StatementEntry corporateAction(int line, LocalDate date, CorporateActionType type, String quantity) {
        return new StatementEntry.CorporateActionEntry(line, date, "row-" + line, "row " + line, MGLU3, type,
                new Quantity(new BigDecimal(quantity)));
    }

    private static final class StubReader implements StatementReader {

        private final EntrySource source;
        private List<StatementEntry> entries = new ArrayList<>();

        StubReader(EntrySource source) {
            this.source = source;
        }

        void returns(StatementEntry... entries) {
            this.entries = List.of(entries);
        }

        @Override
        public EntrySource source() {
            return source;
        }

        @Override
        public Statement read(String fileName, byte[] content) {
            return new Statement(entries, List.of());
        }
    }
}
