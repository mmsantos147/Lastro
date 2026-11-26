package io.matheusms.lastro.adapter.out.binance;

import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.EntrySource;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quantity;
import io.matheusms.lastro.domain.model.TradeType;
import io.matheusms.lastro.domain.model.statement.AssetReference;
import io.matheusms.lastro.domain.model.statement.ImportIssue;
import io.matheusms.lastro.domain.model.statement.ImportIssue.Code;
import io.matheusms.lastro.domain.model.statement.Statement;
import io.matheusms.lastro.domain.model.statement.StatementEntry;
import io.matheusms.lastro.domain.model.statement.TradeLeg;
import io.matheusms.lastro.domain.model.statement.TradeLeg.LegFee;
import io.matheusms.lastro.domain.model.statement.UnreadableStatementException;
import io.matheusms.lastro.domain.port.StatementReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

/**
 * Reads the Binance transaction history ("Histórico de Transações"), exported as CSV (or the zip Binance sends,
 * with one CSV per part). Columns, in Portuguese or English: user id, time, account, operation, coin, change,
 * remark. The user id is never kept.
 * <p>
 * The file is a ledger: each row is one balance change. Rows that belong to the same operation are grouped (same
 * second for trades and conversions, same remark for dust conversions and sales to fiat) and become a conversion
 * from one coin to another, recorded as trades (ADR 0009):
 * <ul>
 *   <li>paid in BRL: a buy priced in BRL; received in BRL: a sell priced in BRL;</li>
 *   <li>paid in a stablecoin (USDT): a buy priced in USD plus a sell of the stablecoin at 1 USD, and the reverse when
 *       a stablecoin is received;</li>
 *   <li>between two other crypto assets (dust conversion): no price in cash is known, so both trades are recorded
 *       at zero and reported.</li>
 * </ul>
 * Fees ("Transaction Fee") are paid in an asset (BNB, or the coin received) and belong to the trade of the non-stable
 * coin. Fiat deposits and withdrawals and moves between Binance accounts do not change positions. Crypto deposits and
 * withdrawals (transfers) and Earn rewards are reported as not supported.
 */
@Component
class BinanceTransactionHistoryReader implements StatementReader {

    private static final String FIAT = "BRL";
    private static final Set<String> STABLECOINS = Set.of("USDT", "USDC", "FDUSD", "DAI", "BUSD", "TUSD");
    private static final List<DateTimeFormatter> TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"), DateTimeFormatter.ofPattern("yy-MM-dd HH:mm:ss"));

    /** Rows of one order, grouped by time. */
    private static final Set<String> TRADE_OPERATIONS = Set.of(
            "transaction buy", "transaction spend", "transaction fee", "transaction sold", "transaction revenue",
            "buy", "sell", "fee", "binance convert", "convert");
    /** Rows of one conversion, grouped by remark (it carries the order id or the pair). */
    private static final Set<String> REMARK_OPERATIONS = Set.of(
            "small assets exchange bnb", "sell crypto to fiat", "buy crypto with fiat", "buy crypto");
    private static final Set<String> CASH_OPERATIONS = Set.of("deposit", "fiat deposit", "withdraw", "fiat withdraw");
    private static final Set<String> INTERNAL_PREFIXES = Set.of(
            "transfer between", "transfer funds", "simple earn flexible subscription",
            "simple earn flexible redemption", "simple earn locked subscription", "simple earn locked redemption",
            "staking purchase", "staking redemption", "main and funding account transfer");

    @Override
    public EntrySource source() {
        return EntrySource.BINANCE_CSV;
    }

    @Override
    public Statement read(String fileName, byte[] content) {
        var rows = new ArrayList<Row>();
        for (String csv : csvFiles(content)) {
            rows.addAll(parse(csv, rows.size()));
        }
        rows.sort(Comparator.comparing(Row::time).thenComparingInt(Row::line));

        var entries = new ArrayList<StatementEntry>();
        var issues = new ArrayList<ImportIssue>();
        var groups = new LinkedHashMap<String, List<Row>>();
        for (Row row : rows) {
            var operation = row.operation();
            if (TRADE_OPERATIONS.contains(operation)) {
                groups.computeIfAbsent("T|" + row.time(), key -> new ArrayList<>()).add(row);
            } else if (REMARK_OPERATIONS.contains(operation)) {
                groups.computeIfAbsent(remarkGroup(row), key -> new ArrayList<>()).add(row);
            } else if (CASH_OPERATIONS.contains(operation)) {
                issues.add(new ImportIssue(row.line(), row.coin().equals(FIAT) ? Code.INFORMATIONAL : Code.UNSUPPORTED,
                        row.description(), row.coin().equals(FIAT) ? null
                                : "Crypto deposits and withdrawals must be recorded as transfers"));
            } else if (INTERNAL_PREFIXES.stream().anyMatch(operation::startsWith)) {
                issues.add(new ImportIssue(row.line(), Code.INFORMATIONAL, row.description()));
            } else {
                issues.add(new ImportIssue(row.line(), Code.UNSUPPORTED, row.description(),
                        "Operation not supported: " + row.rawOperation()));
            }
        }

        var occurrences = new HashMap<String, Integer>();
        for (List<Row> group : groups.values()) {
            var signature = group.stream().map(Row::signature).sorted().collect(Collectors.joining(";"));
            var fingerprint = signature + "#" + occurrences.merge(signature, 1, Integer::sum);
            try {
                conversion(group, fingerprint, entries, issues);
            } catch (RuntimeException exception) {
                issues.add(new ImportIssue(group.getFirst().line(), Code.INVALID_ROW, describe(group),
                        exception.getMessage()));
            }
        }
        entries.sort(Comparator.comparing(StatementEntry::date).thenComparingInt(StatementEntry::line));
        return new Statement(entries, issues);
    }

    /**
     * A dust conversion ("Small Assets Exchange BNB") converts several coins at the same second, one remark per pair
     * ("USDT to BNB"). The legs of a sale to fiat are seconds apart but share the order id in the remark.
     */
    private static String remarkGroup(Row row) {
        if (row.remark().isBlank()) {
            return "T|" + row.time();
        }
        var sameSecond = row.operation().equals("small assets exchange bnb") ? row.time().toString() : "";
        return "R|" + row.operation() + "|" + row.remark() + "|" + sameSecond;
    }

    // --- Conversions ---

    private void conversion(List<Row> group, String fingerprint, List<StatementEntry> entries,
            List<ImportIssue> issues) {
        var changes = new LinkedHashMap<String, BigDecimal>();
        var fees = new LinkedHashMap<String, BigDecimal>();
        for (Row row : group) {
            var target = row.operation().endsWith("fee") ? fees : changes;
            target.merge(row.coin(), row.change(), BigDecimal::add);
        }
        changes.values().removeIf(change -> change.signum() == 0);
        var paid = changes.entrySet().stream().filter(entry -> entry.getValue().signum() < 0).toList();
        var received = changes.entrySet().stream().filter(entry -> entry.getValue().signum() > 0).toList();
        if (paid.size() != 1 || received.size() != 1) {
            throw new IllegalArgumentException("Expected one coin paid and one received, got " + changes);
        }
        if (fees.size() > 1) {
            throw new IllegalArgumentException("Fees paid in more than one coin: " + fees);
        }
        var from = paid.getFirst().getKey();
        var fromAmount = paid.getFirst().getValue().negate();
        var to = received.getFirst().getKey();
        var toAmount = received.getFirst().getValue();
        var line = group.getFirst().line();
        var date = group.stream().filter(row -> !row.coin().equals(FIAT)).findFirst().orElse(group.getFirst())
                .time().toLocalDate();
        var description = describe(group);

        var legs = legs(from, fromAmount, to, toAmount);
        if (legs.isEmpty()) {
            throw new IllegalArgumentException("Conversion between fiat currencies is not supported");
        }
        if (!isCash(from) && !isCash(to)) {
            issues.add(new ImportIssue(line, Code.PRICE_UNAVAILABLE, description,
                    "No cash price for %s to %s; recorded at zero".formatted(from, to)));
        }
        if (!fees.isEmpty()) {
            var fee = fees.entrySet().iterator().next();
            legs = withFee(legs, fee.getKey(), fee.getValue().negate());
        }
        entries.add(new StatementEntry.TradeGroup(line, date, fingerprint, description, legs));
    }

    /** Trades of a conversion of {@code fromAmount} of {@code from} into {@code toAmount} of {@code to}. */
    private static List<TradeLeg> legs(String from, BigDecimal fromAmount, String to, BigDecimal toAmount) {
        if (from.equals(FIAT) && to.equals(FIAT)) {
            return List.of();
        }
        if (from.equals(FIAT)) {
            return List.of(leg(to, TradeType.BUY, toAmount, price(fromAmount, toAmount), Currency.BRL));
        }
        if (to.equals(FIAT)) {
            return List.of(leg(from, TradeType.SELL, fromAmount, price(toAmount, fromAmount), Currency.BRL));
        }
        if (STABLECOINS.contains(from)) {
            return List.of(
                    leg(to, TradeType.BUY, toAmount, price(fromAmount, toAmount), Currency.USD),
                    leg(from, TradeType.SELL, fromAmount, BigDecimal.ONE, Currency.USD));
        }
        if (STABLECOINS.contains(to)) {
            return List.of(
                    leg(from, TradeType.SELL, fromAmount, price(toAmount, fromAmount), Currency.USD),
                    leg(to, TradeType.BUY, toAmount, BigDecimal.ONE, Currency.USD));
        }
        return List.of(
                leg(from, TradeType.SELL, fromAmount, BigDecimal.ZERO, Currency.USD),
                leg(to, TradeType.BUY, toAmount, BigDecimal.ZERO, Currency.USD));
    }

    /** The fee belongs to the trade of the coin that is neither fiat nor a stablecoin (the first one otherwise). */
    private static List<TradeLeg> withFee(List<TradeLeg> legs, String coin, BigDecimal amount) {
        if (coin.equals(FIAT)) {
            throw new IllegalArgumentException("Fees paid in BRL are not supported");
        }
        var target = legs.stream().filter(leg -> !STABLECOINS.contains(leg.asset().code())).findFirst()
                .orElse(legs.getFirst());
        var fee = new LegFee.InAsset(asset(coin), new Quantity(amount));
        return legs.stream()
                .map(leg -> leg == target
                        ? new TradeLeg(leg.asset(), leg.type(), leg.quantity(), leg.unitPrice(), fee)
                        : leg)
                .toList();
    }

    private static TradeLeg leg(String coin, TradeType type, BigDecimal quantity, BigDecimal price, Currency currency) {
        return new TradeLeg(asset(coin), type, new Quantity(quantity), new Money(price, currency), LegFee.NONE);
    }

    private static BigDecimal price(BigDecimal total, BigDecimal quantity) {
        return total.divide(quantity, 12, RoundingMode.HALF_EVEN);
    }

    private static boolean isCash(String coin) {
        return coin.equals(FIAT) || STABLECOINS.contains(coin);
    }

    private static AssetReference asset(String coin) {
        return new AssetReference(coin, coin, AssetClass.CRYPTO, Currency.USD);
    }

    /** E.g. {@code "2025-01-07 18:07:18 · Transaction Buy · 0.00016 BTC, -15.491808 USDT"}. */
    private static String describe(List<Row> group) {
        var changes = group.stream().filter(row -> !row.operation().endsWith("fee")).toList();
        var main = changes.stream()
                .filter(row -> row.operation().endsWith("buy") || row.operation().endsWith("sold"))
                .findFirst()
                .orElse(changes.isEmpty() ? group.getFirst() : changes.getFirst());
        var coins = changes.stream()
                .sorted(Comparator.comparing(row -> row.change().signum() < 0))
                .map(row -> row.change().stripTrailingZeros().toPlainString() + " " + row.coin())
                .collect(Collectors.joining(", "));
        return "%s · %s · %s".formatted(main.time().format(TIME_FORMATS.getFirst()), main.rawOperation(), coins);
    }

    // --- CSV ---

    private record Row(int line, LocalDateTime time, String rawOperation, String coin, BigDecimal change,
            String remark) {

        String operation() {
            return normalize(rawOperation);
        }

        String signature() {
            return String.join("|", time.toString(), operation(), coin, change.stripTrailingZeros().toPlainString(),
                    remark);
        }

        String description() {
            return "%s · %s · %s %s".formatted(time.format(TIME_FORMATS.getFirst()), rawOperation,
                    change.stripTrailingZeros().toPlainString(), coin);
        }
    }

    /** The CSV itself, or every CSV inside the zip Binance sends. */
    private static List<String> csvFiles(byte[] content) {
        boolean zip = content.length > 3 && content[0] == 'P' && content[1] == 'K';
        if (!zip) {
            return List.of(new String(content, StandardCharsets.UTF_8));
        }
        var files = new ArrayList<String>();
        try (var stream = new ZipInputStream(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
            for (var entry = stream.getNextEntry(); entry != null; entry = stream.getNextEntry()) {
                if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".csv")) {
                    files.add(new String(stream.readNBytes(MAX_CSV_BYTES), StandardCharsets.UTF_8));
                }
            }
        } catch (IOException exception) {
            throw new UnreadableStatementException("Invalid zip file", exception);
        }
        if (files.isEmpty()) {
            throw new UnreadableStatementException("The zip file has no CSV");
        }
        return files;
    }

    /** Guards against zip bombs: a transaction history is far smaller. */
    private static final int MAX_CSV_BYTES = 50 * 1024 * 1024;

    private static List<Row> parse(String csv, int lineOffset) {
        var lines = csv.replace("﻿", "").lines().toList();
        if (lines.isEmpty()) {
            throw new UnreadableStatementException("The CSV is empty");
        }
        var header = fields(lines.getFirst()).stream().map(BinanceTransactionHistoryReader::normalize).toList();
        int time = column(header, "tempo", "utc_time", "time", "date(utc)");
        int operation = column(header, "operacao", "operation");
        int coin = column(header, "moeda", "coin");
        int change = column(header, "alterar", "change");
        int remark = optionalColumn(header, "observacao", "remark");
        var rows = new ArrayList<Row>();
        for (int index = 1; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) {
                continue;
            }
            var values = fields(lines.get(index));
            int line = lineOffset + index + 1;
            try {
                rows.add(new Row(line, time(values.get(time)), values.get(operation).strip(),
                        values.get(coin).strip().toUpperCase(), new BigDecimal(values.get(change).strip()),
                        remark >= 0 && remark < values.size() ? values.get(remark).strip() : ""));
            } catch (RuntimeException exception) {
                throw new UnreadableStatementException("Invalid row at line %d: %s".formatted(line,
                        exception.getMessage()), exception);
            }
        }
        return rows;
    }

    private static int column(List<String> header, String... names) {
        int index = optionalColumn(header, names);
        if (index < 0) {
            throw new UnreadableStatementException("Not a Binance transaction history: missing column " + names[0]);
        }
        return index;
    }

    private static int optionalColumn(List<String> header, String... names) {
        for (String name : names) {
            int index = header.indexOf(name);
            if (index >= 0) {
                return index;
            }
        }
        return -1;
    }

    private static LocalDateTime time(String value) {
        for (DateTimeFormatter format : TIME_FORMATS) {
            try {
                return LocalDateTime.parse(value.strip(), format);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        throw new IllegalArgumentException("Invalid time: " + value);
    }

    /** Splits a CSV line, honoring double quotes. */
    static List<String> fields(String line) {
        var fields = new ArrayList<String>();
        var current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().strip()
                .replaceAll("\\s+", " ");
    }
}
