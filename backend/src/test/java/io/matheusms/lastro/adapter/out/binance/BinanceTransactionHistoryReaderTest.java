package io.matheusms.lastro.adapter.out.binance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quantity;
import io.matheusms.lastro.domain.model.TradeType;
import io.matheusms.lastro.domain.model.statement.ImportIssue;
import io.matheusms.lastro.domain.model.statement.Statement;
import io.matheusms.lastro.domain.model.statement.StatementEntry;
import io.matheusms.lastro.domain.model.statement.TradeLeg;
import io.matheusms.lastro.domain.model.statement.UnreadableStatementException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class BinanceTransactionHistoryReaderTest {

    private static final String HEADER = "ID do Usuário,Tempo,Conta,Operação,Moeda,Alterar,Observação\n";

    private final BinanceTransactionHistoryReader reader = new BinanceTransactionHistoryReader();

    @Test
    void shouldReadABuyPaidInBrlWithTheFeeInTheCoinBought() {
        var statement = read("""
                1,2025-01-07 18:03:16,Spot,Deposit,BRL,400,
                1,2025-01-07 18:05:27,Spot,Transaction Buy,USDT,65.2,
                1,2025-01-07 18:05:27,Spot,Transaction Spend,BRL,-399.8716,
                1,2025-01-07 18:05:27,Spot,Transaction Fee,USDT,-0.0652,
                """);

        var legs = onlyGroup(statement).legs();
        assertThat(legs).hasSize(1);
        var buy = legs.getFirst();
        assertThat(buy.asset().code()).isEqualTo("USDT");
        assertThat(buy.type()).isEqualTo(TradeType.BUY);
        assertThat(buy.quantity()).isEqualTo(Quantity.of("65.2"));
        assertThat(buy.unitPrice()).isEqualTo(Money.brl("6.133"));
        assertThat(buy.fee()).isEqualTo(new TradeLeg.LegFee.InAsset(buy.asset(), Quantity.of("0.0652")));
        assertThat(statement.issues()).extracting(ImportIssue::code).containsExactly(ImportIssue.Code.INFORMATIONAL);
    }

    @Test
    void shouldTurnABuyPaidInUsdtIntoABuyAndASellOfUsdt() {
        var statement = read("""
                1,2025-01-07 18:07:18,Spot,Transaction Fee,BNB,-0.00000016,
                1,2025-01-07 18:07:18,Spot,Transaction Spend,USDT,-15.491808,
                1,2025-01-07 18:07:18,Spot,Transaction Buy,BTC,0.00016,
                """);

        var legs = onlyGroup(statement).legs();
        assertThat(legs).extracting(leg -> leg.asset().code(), TradeLeg::type).containsExactly(
                org.assertj.core.groups.Tuple.tuple("BTC", TradeType.BUY),
                org.assertj.core.groups.Tuple.tuple("USDT", TradeType.SELL));
        assertThat(legs.getFirst().unitPrice()).isEqualTo(Money.of("96823.8", Currency.USD));
        assertThat(legs.getFirst().fee()).isInstanceOf(TradeLeg.LegFee.InAsset.class);
        assertThat(legs.getLast().unitPrice()).isEqualTo(Money.of("1", Currency.USD));
        assertThat(legs.getLast().quantity()).isEqualTo(Quantity.of("15.491808"));
        assertThat(legs.getLast().fee()).isEqualTo(TradeLeg.LegFee.NONE);
    }

    @Test
    void shouldAddUpFillsOfTheSameOrder() {
        var statement = read("""
                1,2025-10-12 10:26:37,Spot,Transaction Buy,ETH,0.0015,
                1,2025-10-12 10:26:37,Spot,Transaction Fee,BNB,-0.00000346,
                1,2025-10-12 10:26:37,Spot,Transaction Buy,ETH,0.001,
                1,2025-10-12 10:26:37,Spot,Transaction Spend,USDT,-3.80662,
                1,2025-10-12 10:26:37,Spot,Transaction Spend,USDT,-5.70993,
                1,2025-10-12 10:26:37,Spot,Transaction Fee,BNB,-0.00000231,
                """);

        var buy = onlyGroup(statement).legs().getFirst();
        assertThat(buy.quantity()).isEqualTo(Quantity.of("0.0025"));
        assertThat(buy.fee()).isEqualTo(new TradeLeg.LegFee.InAsset(
                new io.matheusms.lastro.domain.model.statement.AssetReference("BNB", "BNB",
                        io.matheusms.lastro.domain.model.AssetClass.CRYPTO, Currency.USD),
                Quantity.of("0.00000577")));
    }

    @Test
    void shouldReadSalesToUsdtAndToBrl() {
        var statement = read("""
                1,2025-08-09 10:54:04,Spot,Transaction Revenue,USDT,24.12,
                1,2025-08-09 10:54:04,Spot,Transaction Fee,BNB,-0.0000224,
                1,2025-08-09 10:54:04,Spot,Transaction Sold,SOL,-0.134,
                1,2025-08-09 10:57:05,Spot,Transaction Sold,USDT,-24.1,
                1,2025-08-09 10:57:05,Spot,Transaction Revenue,BRL,131.74265,
                1,2025-08-09 10:57:05,Spot,Transaction Fee,BNB,-0.00002235,
                """);

        var groups = groups(statement);
        assertThat(groups.getFirst().legs()).extracting(leg -> leg.asset().code(), TradeLeg::type).containsExactly(
                org.assertj.core.groups.Tuple.tuple("SOL", TradeType.SELL),
                org.assertj.core.groups.Tuple.tuple("USDT", TradeType.BUY));
        assertThat(groups.getFirst().legs().getFirst().fee()).isInstanceOf(TradeLeg.LegFee.InAsset.class);
        var toBrl = groups.getLast().legs();
        assertThat(toBrl).hasSize(1);
        assertThat(toBrl.getFirst().type()).isEqualTo(TradeType.SELL);
        assertThat(toBrl.getFirst().unitPrice().currency()).isEqualTo(Currency.BRL);
    }

    @Test
    void shouldSplitADustConversionByPairAndPairSalesToFiatByOrder() {
        var statement = read("""
                1,2025-01-28 19:56:52,Spot,Small Assets Exchange BNB,BRL,-0.5,BRL to BNB
                1,2025-01-28 19:56:52,Spot,Small Assets Exchange BNB,BNB,0.00012561,BRL to BNB
                1,2025-01-28 19:56:52,Spot,Small Assets Exchange BNB,USDT,-0.0460566,USDT to BNB
                1,2025-01-28 19:56:52,Spot,Small Assets Exchange BNB,BNB,0.00006798,USDT to BNB
                1,2025-09-25 13:08:21,Spot,Small Assets Exchange BNB,SOL,-0.000914,SOL to BNB
                1,2025-09-25 13:08:21,Spot,Small Assets Exchange BNB,BNB,0.0001811,SOL to BNB
                1,2026-01-27 16:45:18,Spot,Sell Crypto to Fiat,ETH,-0.007,Via CashBalance - Wallet/N1
                1,2026-01-27 16:45:23,Spot,Sell Crypto to Fiat,BRL,108.02,Via CashBalance - Wallet/N1
                """);

        var groups = groups(statement);
        assertThat(groups).hasSize(4);
        assertThat(groups.get(0).legs()).hasSize(1);
        assertThat(groups.get(1).legs()).hasSize(2);
        assertThat(groups.get(2).legs()).extracting(leg -> leg.unitPrice().amount().signum()).containsOnly(0);
        var sale = groups.get(3);
        assertThat(sale.date()).isEqualTo(LocalDate.of(2026, 1, 27));
        assertThat(sale.legs().getFirst().unitPrice()).isEqualTo(Money.brl("15431.42857143"));
        assertThat(statement.issues()).extracting(ImportIssue::code).containsExactly(ImportIssue.Code.PRICE_UNAVAILABLE);
    }

    @Test
    void shouldReportCryptoTransfersAndUnknownOperations() {
        var statement = read("""
                1,2025-03-01 10:00:00,Spot,Deposit,BTC,0.01,
                1,2025-03-02 10:00:00,Earn,Simple Earn Flexible Interest,USDT,0.01,
                1,2025-03-03 10:00:00,Spot,Transfer Between Main and Funding Wallet,USDT,-1,
                """);

        assertThat(statement.entries()).isEmpty();
        assertThat(statement.issues()).extracting(ImportIssue::code).containsExactly(
                ImportIssue.Code.UNSUPPORTED, ImportIssue.Code.UNSUPPORTED, ImportIssue.Code.INFORMATIONAL);
    }

    @Test
    void shouldReadTheZipBinanceSendsAndEnglishHeaders() throws IOException {
        var csv = """
                User_ID,UTC_Time,Account,Operation,Coin,Change,Remark
                1,2025-01-07 18:07:58,Spot,Transaction Buy,ETH,0.0047,
                1,2025-01-07 18:07:58,Spot,Transaction Spend,USDT,-15.985781,
                """;
        var zip = new ByteArrayOutputStream();
        try (var out = new ZipOutputStream(zip)) {
            out.putNextEntry(new ZipEntry("history-part1-of1.csv"));
            out.write(csv.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        var statement = reader.read("history.zip", zip.toByteArray());

        assertThat(onlyGroup(statement).legs().getFirst().asset().code()).isEqualTo("ETH");
    }

    @Test
    void shouldGiveTheSameRowsTheSameFingerprint() {
        var csv = """
                1,2025-01-07 18:07:58,Spot,Transaction Buy,ETH,0.0047,
                1,2025-01-07 18:07:58,Spot,Transaction Spend,USDT,-15.985781,
                """;

        assertThat(onlyGroup(read(csv)).fingerprint()).isEqualTo(onlyGroup(read(csv)).fingerprint());
    }

    @Test
    void shouldRejectOtherFiles() {
        assertThatThrownBy(() -> reader.read("other.csv", "a,b,c\n1,2,3\n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(UnreadableStatementException.class);
    }

    @Test
    void shouldSplitQuotedFields() {
        assertThat(BinanceTransactionHistoryReader.fields("1,\"a, \"\"b\"\"\",c"))
                .containsExactly("1", "a, \"b\"", "c");
    }

    private Statement read(String rows) {
        return reader.read("history.csv", (HEADER + rows).getBytes(StandardCharsets.UTF_8));
    }

    private static StatementEntry.TradeGroup onlyGroup(Statement statement) {
        assertThat(statement.entries()).hasSize(1);
        return (StatementEntry.TradeGroup) statement.entries().getFirst();
    }

    private static List<StatementEntry.TradeGroup> groups(Statement statement) {
        return statement.entries().stream().map(StatementEntry.TradeGroup.class::cast).toList();
    }
}
