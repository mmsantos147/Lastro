package io.matheusms.lastro.adapter.out.b3import;

import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.CorporateActionType;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.DistributionType;
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
import io.matheusms.lastro.domain.model.statement.UnreadableStatementException;
import io.matheusms.lastro.domain.port.StatementReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

/**
 * Reads the "Movimentação" spreadsheet exported from B3's Área do Investidor (Extratos → Movimentação → Excel).
 * Columns: Entrada/Saída, Data, Movimentação, Produto, Instituição, Quantidade, Preço unitário, Valor da Operação.
 * Rows come newest first; entries are returned oldest first.
 * <p>
 * Meaning of the movements, as observed in real exports:
 * <ul>
 *   <li>"Transferência - Liquidação": settlement of a trade (credit = buy, debit = sell). "Compra"/"Venda" and the
 *       debit "Resgate"/"Vencimento" are trades of Tesouro Direto. Fees are not in the file.</li>
 *   <li>"Rendimento", "Dividendo", "Juros Sobre Capital Próprio": income; the value is the amount credited.</li>
 *   <li>"Desdobro" and "Bonificação em Ativos" report the shares received; "Grupamento", the shares held after.</li>
 *   <li>"Direitos de Subscrição - Exercido": purchase of new shares of the fund (rights {@code XXXX12} and
 *       receipts {@code XXXX13} become {@code XXXX11}).</li>
 *   <li>"Incorporação" (merger) does not say which asset was merged, so it must be registered by the user.</li>
 *   <li>Private fixed income ("CDB - CDB6255F9RH"): "Aplicação" is a buy and a debit "Resgate antecipado" a sell, of
 *       units of R$ 0,01; each application has its own code, which becomes the asset.</li>
 *   <li>Custody transfers, subscription rights, balance updates ("Atualização") and fraction movements do not
 *       change positions.</li>
 * </ul>
 */
@Component
class B3MovementReader implements StatementReader {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern TICKER = Pattern.compile("[A-Z0-9]{4}\\d{1,2}[A-Z]?");
    private static final Pattern FUND_RIGHT = Pattern.compile("([A-Z0-9]{4})1[23]");
    private static final Pattern YEAR = Pattern.compile("(\\d{4})\\s*$");
    private static final Set<String> FIXED_INCOME_PREFIXES =
            Set.of("CDB", "LCI", "LCA", "LC", "CRI", "CRA", "DEB", "LF", "RDB", "LIG");
    private static final Set<String> INFORMATIONAL = Set.of(
            "transferencia", "atualizacao", "fracao em ativos", "direito de subscricao",
            "direitos de subscricao - nao exercido", "cessao de direitos", "cessao de direitos - solicitada",
            "direito sobras de subscricao", "direito sobras de subscricao - nao exercido", "recibo de subscricao",
            "solicitacao de subscricao", "sobras de subscricao", "recibo de subscricao - exercido");

    private final DataFormatter formatter = new DataFormatter();

    @Override
    public EntrySource source() {
        return EntrySource.B3_IMPORT;
    }

    @Override
    public Statement read(String fileName, byte[] content) {
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            return read(workbook.getSheetAt(0));
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof UnreadableStatementException unreadable) {
                throw unreadable;
            }
            throw new UnreadableStatementException("Not a B3 movements spreadsheet: " + exception.getMessage(),
                    exception);
        }
    }

    private Statement read(Sheet sheet) {
        var columns = columns(sheet.getRow(sheet.getFirstRowNum()));
        var rows = new ArrayList<RawRow>();
        var issues = new ArrayList<ImportIssue>();
        for (int index = sheet.getFirstRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
            var row = sheet.getRow(index);
            if (row == null || text(row, columns.movement()).isBlank()) {
                continue;
            }
            try {
                rows.add(raw(row, columns));
            } catch (RuntimeException exception) {
                issues.add(new ImportIssue(index + 1, Code.INVALID_ROW,
                        text(row, columns.movement()) + " · " + text(row, columns.product()), exception.getMessage()));
            }
        }
        var entries = new ArrayList<StatementEntry>();
        var occurrences = new HashMap<String, Integer>();
        for (RawRow row : rows.reversed()) {
            var fingerprint = row.signature() + "#" + occurrences.merge(row.signature(), 1, Integer::sum);
            try {
                interpret(row, fingerprint, entries, issues);
            } catch (RuntimeException exception) {
                issues.add(new ImportIssue(row.line(), Code.INVALID_ROW, row.description(), exception.getMessage()));
            }
        }
        return new Statement(entries, issues);
    }

    // --- Interpretation ---

    private void interpret(RawRow row, String fingerprint, List<StatementEntry> entries, List<ImportIssue> issues) {
        var movement = normalize(row.movement()).replaceAll("[/\\s]+$", "");
        if (isFixedIncome(row.product())) {
            fixedIncome(row, fingerprint, movement, entries, issues);
            return;
        }
        if (INFORMATIONAL.contains(movement)) {
            issues.add(issue(row, Code.INFORMATIONAL));
            return;
        }
        switch (movement) {
            case "transferencia - liquidacao" -> entries.add(trade(row, fingerprint, row.isCredit()
                    ? TradeType.BUY : TradeType.SELL, asset(row)));
            case "compra" -> entries.add(trade(row, fingerprint, TradeType.BUY, asset(row)));
            case "venda" -> entries.add(trade(row, fingerprint, TradeType.SELL, asset(row)));
            case "resgate", "vencimento" -> {
                if (row.isCredit()) {
                    issues.add(issue(row, Code.CORPORATE_EVENT_CASH));
                } else {
                    entries.add(trade(row, fingerprint, TradeType.SELL, asset(row)));
                }
            }
            case "direitos de subscricao - exercido" -> exercisedRight(row, fingerprint, entries, issues);
            case "rendimento" -> {
                var asset = asset(row);
                entries.add(distribution(row, fingerprint, asset,
                        asset.assetClass() == AssetClass.FII ? DistributionType.FII_INCOME : DistributionType.DIVIDEND));
            }
            case "dividendo" -> entries.add(distribution(row, fingerprint, asset(row), DistributionType.DIVIDEND));
            case "juros sobre capital proprio" -> entries.add(distribution(row, fingerprint, asset(row),
                    DistributionType.JCP));
            case "desdobro", "desdobramento" -> entries.add(corporateAction(row, fingerprint,
                    CorporateActionType.STOCK_SPLIT));
            case "grupamento" -> entries.add(corporateAction(row, fingerprint, CorporateActionType.REVERSE_SPLIT));
            case "bonificacao em ativos" -> entries.add(corporateAction(row, fingerprint,
                    CorporateActionType.BONUS_SHARES));
            case "incorporacao" -> issues.add(issue(row, Code.MERGER_REQUIRED));
            case "leilao de fracao" -> issues.add(issue(row, Code.CORPORATE_EVENT_CASH));
            default -> issues.add(new ImportIssue(row.line(), Code.UNSUPPORTED, row.description(),
                    "Movement not supported: " + row.movement()));
        }
    }

    private StatementEntry trade(RawRow row, String fingerprint, TradeType type, AssetReference asset) {
        var quantity = row.quantity().orElseThrow(() -> new IllegalArgumentException("Missing quantity"));
        var unitPrice = row.unitPrice()
                .or(() -> row.value().map(value -> value.divide(quantity.value(), 8, RoundingMode.HALF_EVEN)))
                .orElseThrow(() -> new IllegalArgumentException("Missing price"));
        var leg = new TradeLeg(asset, type, quantity, new Money(unitPrice, Currency.BRL), TradeLeg.LegFee.NONE);
        return new StatementEntry.TradeGroup(row.line(), row.date(), fingerprint, row.description(), List.of(leg));
    }

    /**
     * Private fixed income (CDB, LCI, LCA): "Aplicação" is a purchase and the debit "Resgate antecipado", "Resgate" or
     * "Vencimento" a redemption, in units (e.g. 15000 units of R$ 0,01). The terms (rate, maturity) are not in the
     * file.
     */
    private void fixedIncome(RawRow row, String fingerprint, String movement, List<StatementEntry> entries,
            List<ImportIssue> issues) {
        var asset = fixedIncomeAsset(row);
        if (movement.equals("aplicacao")) {
            entries.add(trade(row, fingerprint, TradeType.BUY, asset));
        } else if (!row.isCredit() && (movement.startsWith("resgate") || movement.equals("vencimento"))) {
            entries.add(trade(row, fingerprint, TradeType.SELL, asset));
        } else {
            issues.add(new ImportIssue(row.line(), Code.UNSUPPORTED, row.description(),
                    "Fixed income movement not supported: " + row.movement()));
        }
    }

    /** "CDB - CDB6255F9RH - BANCO INTER S/A" or "CDB - CDB9268A5NL" (the issuer then comes from the institution). */
    static AssetReference fixedIncomeAsset(RawRow row) {
        var parts = row.product().strip().split("\\s+[-–]\\s+");
        if (parts.length < 2 || parts[1].isBlank()) {
            throw new IllegalArgumentException("Fixed income product without a code: " + row.product());
        }
        var type = parts[0].strip().toUpperCase();
        var code = parts[1].strip().toUpperCase();
        var issuer = parts.length > 2 ? parts[2].strip() : row.institution();
        var name = "%s %s (%s)".formatted(type, issuer, code).strip();
        return new AssetReference(code, name, AssetClass.FIXED_INCOME, Currency.BRL);
    }

    /** Subscription of new fund shares: the right ({@code XXXX12}) is exercised into {@code XXXX11}. */
    private void exercisedRight(RawRow row, String fingerprint, List<StatementEntry> entries,
            List<ImportIssue> issues) {
        var right = asset(row);
        var matcher = FUND_RIGHT.matcher(right.code());
        if (!matcher.matches() || row.unitPrice().isEmpty()) {
            issues.add(new ImportIssue(row.line(), Code.UNSUPPORTED, row.description(),
                    "Exercised right without a known underlying asset"));
            return;
        }
        var shares = new AssetReference(matcher.group(1) + "11", right.name(), right.assetClass(), Currency.BRL);
        entries.add(trade(row, fingerprint, TradeType.BUY, shares));
    }

    private StatementEntry distribution(RawRow row, String fingerprint, AssetReference asset, DistributionType type) {
        var amount = row.value().orElseThrow(() -> new IllegalArgumentException("Missing value"));
        return new StatementEntry.DistributionEntry(row.line(), row.date(), fingerprint, row.description(), asset,
                type, row.quantity().orElse(Quantity.ZERO), new Money(amount, Currency.BRL));
    }

    private StatementEntry corporateAction(RawRow row, String fingerprint, CorporateActionType type) {
        var quantity = row.quantity().orElseThrow(() -> new IllegalArgumentException("Missing quantity"));
        return new StatementEntry.CorporateActionEntry(row.line(), row.date(), fingerprint, row.description(),
                asset(row), type, quantity);
    }

    private static ImportIssue issue(RawRow row, Code code) {
        return new ImportIssue(row.line(), code, row.description());
    }

    // --- Assets ---

    private static boolean isFixedIncome(String product) {
        var prefix = product.strip().split("[\\s-]+", 2)[0].toUpperCase();
        return FIXED_INCOME_PREFIXES.contains(prefix);
    }

    /** "PETR4 - PETROLEO BRASILEIRO S.A." or "Tesouro IPCA+ 2029". */
    static AssetReference asset(RawRow row) {
        var product = row.product().strip();
        if (normalize(product).startsWith("tesouro")) {
            return new AssetReference(treasuryCode(product), product, AssetClass.TREASURY, Currency.BRL);
        }
        var parts = product.split("\\s+[-–]\\s+", 2);
        var code = parts[0].strip().toUpperCase();
        if (!TICKER.matcher(code).matches()) {
            throw new IllegalArgumentException("Unknown product: " + product);
        }
        var name = parts.length > 1 ? parts[1].strip() : code;
        return new AssetReference(code, name, assetClass(name), Currency.BRL);
    }

    private static AssetClass assetClass(String name) {
        var normalized = " " + normalize(name).replaceAll("[^a-z0-9]+", " ") + " ";
        if (normalized.contains(" fii ") || normalized.contains(" imob") || normalized.contains(" fiagro ")
                || normalized.contains(" imobiliario") || normalized.contains(" fundo de fundos ")) {
            return AssetClass.FII;
        }
        if (normalized.contains(" etf ") || normalized.contains(" index ") || normalized.contains(" ishares ")
                || normalized.contains(" indice ")) {
            return AssetClass.ETF;
        }
        return AssetClass.STOCK;
    }

    /** Same convention as the manual catalog: {@code TESOURO-IPCA-2035}, {@code TESOURO-PRE-JS-2031}. */
    static String treasuryCode(String product) {
        var normalized = normalize(product);
        var year = YEAR.matcher(normalized);
        if (!year.find()) {
            throw new IllegalArgumentException("Unknown Tesouro Direto bond: " + product);
        }
        String kind;
        if (normalized.contains("ipca")) {
            kind = "IPCA";
        } else if (normalized.contains("prefixado")) {
            kind = "PRE";
        } else if (normalized.contains("selic")) {
            kind = "SELIC";
        } else if (normalized.contains("renda")) {
            kind = "RENDA";
        } else if (normalized.contains("educa")) {
            kind = "EDUCA";
        } else if (normalized.contains("igpm")) {
            kind = "IGPM";
        } else {
            throw new IllegalArgumentException("Unknown Tesouro Direto bond: " + product);
        }
        var coupons = normalized.contains("juros semestrais") ? "-JS" : "";
        return "TESOURO-" + kind + coupons + "-" + year.group(1);
    }

    // --- Cells ---

    private record Columns(int direction, int date, int movement, int product, int institution, int quantity,
            int unitPrice, int value) {
    }

    private Columns columns(Row header) {
        if (header == null) {
            throw new UnreadableStatementException("The spreadsheet is empty");
        }
        Map<String, Integer> byName = new HashMap<>();
        header.forEach(cell -> byName.put(normalize(formatter.formatCellValue(cell)), cell.getColumnIndex()));
        return new Columns(
                column(byName, "entrada/saida"), column(byName, "data"), column(byName, "movimentacao"),
                column(byName, "produto"), column(byName, "instituicao"), column(byName, "quantidade"),
                column(byName, "preco unitario"), column(byName, "valor da operacao"));
    }

    private static int column(Map<String, Integer> byName, String name) {
        var index = byName.get(name);
        if (index == null) {
            throw new UnreadableStatementException("Missing column: " + name);
        }
        return index;
    }

    private RawRow raw(Row row, Columns columns) {
        return new RawRow(row.getRowNum() + 1, text(row, columns.direction()), date(row.getCell(columns.date())),
                text(row, columns.movement()), text(row, columns.product()), text(row, columns.institution()),
                number(row.getCell(columns.quantity())), number(row.getCell(columns.unitPrice())),
                number(row.getCell(columns.value())));
    }

    private String text(Row row, int column) {
        var cell = row.getCell(column);
        return cell == null ? "" : formatter.formatCellValue(cell).strip();
    }

    private LocalDate date(Cell cell) {
        if (cell == null) {
            throw new IllegalArgumentException("Missing date");
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        try {
            return LocalDate.parse(formatter.formatCellValue(cell).strip(), DATE);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid date: " + formatter.formatCellValue(cell), exception);
        }
    }

    /** Numeric cell, or text in either notation ("1234.5", "1.234,50"); empty for blanks and "-". */
    private Optional<BigDecimal> number(Cell cell) {
        if (cell == null) {
            return Optional.empty();
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return Optional.of(BigDecimal.valueOf(cell.getNumericCellValue()));
        }
        var text = formatter.formatCellValue(cell).strip().replace("R$", "").strip();
        if (text.isEmpty() || text.equals("-")) {
            return Optional.empty();
        }
        if (text.contains(",")) {
            text = text.replace(".", "").replace(",", ".");
        }
        return Optional.of(new BigDecimal(text));
    }

    static String normalize(String text) {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .strip()
                .replaceAll("\\s+", " ");
    }

    record RawRow(int line, String direction, LocalDate date, String movement, String product, String institution,
            Optional<BigDecimal> rawQuantity, Optional<BigDecimal> unitPrice, Optional<BigDecimal> value) {

        boolean isCredit() {
            return normalize(direction).startsWith("credito");
        }

        Optional<Quantity> quantity() {
            return rawQuantity.filter(quantity -> quantity.signum() > 0).map(Quantity::new);
        }

        /** Identifies the row's content, so the same row in another export is recognized. */
        String signature() {
            return String.join("|", normalize(direction), date.toString(), normalize(movement), normalize(product),
                    normalize(institution), rawQuantity.map(BigDecimal::toPlainString).orElse(""),
                    unitPrice.map(BigDecimal::toPlainString).orElse(""),
                    value.map(BigDecimal::toPlainString).orElse(""));
        }

        String description() {
            var code = product.split("\\s+[-–]\\s+", 2)[0].strip();
            var quantityText = rawQuantity.map(quantity -> " · " + quantity.stripTrailingZeros().toPlainString())
                    .orElse("");
            return "%s · %s · %s%s".formatted(date.format(DATE), movement, code, quantityText);
        }
    }
}
