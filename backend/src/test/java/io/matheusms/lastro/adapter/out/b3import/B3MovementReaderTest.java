package io.matheusms.lastro.adapter.out.b3import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.CorporateActionType;
import io.matheusms.lastro.domain.model.DistributionType;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quantity;
import io.matheusms.lastro.domain.model.TradeType;
import io.matheusms.lastro.domain.model.statement.ImportIssue;
import io.matheusms.lastro.domain.model.statement.Statement;
import io.matheusms.lastro.domain.model.statement.StatementEntry;
import io.matheusms.lastro.domain.model.statement.UnreadableStatementException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class B3MovementReaderTest {

    private static final String HEADER = "Entrada/Saída|Data|Movimentação|Produto|Instituição|Quantidade|"
            + "Preço unitário|Valor da Operação";
    private static final String BROKER = "CORRETORA EXEMPLO S.A.";

    private final B3MovementReader reader = new B3MovementReader();

    @Test
    void shouldReadTradesOldestFirst() {
        var statement = read(
                row("Debito", "21/08/2024", "Transferência - Liquidação", "OIBR3 - OI S.A.", 5, 4.38, 21.9),
                row("Credito", "04/02/2022", "Transferência - Liquidação", "OIBR3 - OI S.A.", 500, 1.05, 525));

        var trades = statement.entries().stream().map(StatementEntry.TradeGroup.class::cast).toList();
        assertThat(trades).extracting(StatementEntry::date)
                .containsExactly(LocalDate.of(2022, 2, 4), LocalDate.of(2024, 8, 21));
        var buy = trades.getFirst().legs().getFirst();
        assertThat(buy.type()).isEqualTo(TradeType.BUY);
        assertThat(buy.asset().code()).isEqualTo("OIBR3");
        assertThat(buy.asset().assetClass()).isEqualTo(AssetClass.STOCK);
        assertThat(buy.quantity()).isEqualTo(Quantity.of(500));
        assertThat(buy.unitPrice()).isEqualTo(Money.brl("1.05"));
        assertThat(trades.getLast().legs().getFirst().type()).isEqualTo(TradeType.SELL);
    }

    @Test
    void shouldReadTesouroDiretoTradesWithTheCatalogCode() {
        var statement = read(
                row("Debito", "01/07/2024", "Resgate", "Tesouro Prefixado 2024", 0.12, 1000, 120),
                row("Credito", "02/08/2021", "Compra", "Tesouro IPCA+ com Juros Semestrais 2035", 0.12, 789.31, 94.72));

        var legs = statement.entries().stream()
                .map(entry -> ((StatementEntry.TradeGroup) entry).legs().getFirst())
                .toList();
        assertThat(legs).extracting(leg -> leg.asset().code())
                .containsExactly("TESOURO-IPCA-JS-2035", "TESOURO-PRE-2024");
        assertThat(legs).extracting(leg -> leg.asset().assetClass()).containsOnly(AssetClass.TREASURY);
        assertThat(legs).extracting(leg -> leg.type()).containsExactly(TradeType.BUY, TradeType.SELL);
    }

    @Test
    void shouldReadIncome() {
        var statement = read(
                row("Credito", "16/11/2026", "Rendimento", "MXRF11 - MAXI RENDA FDO INV IMOB - FII", 25, 0.095, 2.37),
                row("Credito", "03/11/2026", "Juros Sobre Capital Próprio", "ITUB4 - ITAU UNIBANCO HOLDING S.A", 3,
                        0.018, 0.05),
                row("Credito", "29/05/2026", "Dividendo", "AUAU3 - UNIÃO PET PARTICIPAÇÕES S.A.", 100, 0.03, 3.02));

        var income = statement.entries().stream().map(StatementEntry.DistributionEntry.class::cast).toList();
        assertThat(income).extracting(StatementEntry.DistributionEntry::type)
                .containsExactly(DistributionType.DIVIDEND, DistributionType.JCP, DistributionType.FII_INCOME);
        var fii = income.getLast();
        assertThat(fii.asset().assetClass()).isEqualTo(AssetClass.FII);
        assertThat(fii.amount()).isEqualTo(Money.brl("2.37"));
        assertThat(fii.baseQuantity()).isEqualTo(Quantity.of(25));
    }

    @Test
    void shouldReadSplitsReverseSplitsAndBonusesAsQuantities() {
        var statement = read(
                row("Credito", "13/10/2026", "Grupamento", "ENJU3 - ENJOEI S.A.", 10, "-", "-"),
                row("Credito", "02/01/2026", "Bonificação em Ativos", "MGLU3 - MAGAZINE LUIZA S.A.", 0.5, "-", "-"),
                row("Credito", "30/11/2023", "Desdobro", "BCFF11 - FDO INV IMOB - FII BTG PACTUAL", 56, "-", "-"));

        assertThat(statement.entries()).map(StatementEntry.CorporateActionEntry.class::cast)
                .extracting(StatementEntry.CorporateActionEntry::type, StatementEntry.CorporateActionEntry::quantity)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(CorporateActionType.STOCK_SPLIT, Quantity.of(56)),
                        org.assertj.core.groups.Tuple.tuple(CorporateActionType.BONUS_SHARES, Quantity.of("0.5")),
                        org.assertj.core.groups.Tuple.tuple(CorporateActionType.REVERSE_SPLIT, Quantity.of(10)));
    }

    @Test
    void shouldTurnAnExercisedFundRightIntoABuyOfTheFund() {
        var statement = read(row("Debito", "22/10/2025", "Direitos de Subscrição - Exercido",
                "MXRF12 - MAXI RENDA FDO INV IMOB - FII", 2, 9.64, 19.28));

        var leg = ((StatementEntry.TradeGroup) statement.entries().getFirst()).legs().getFirst();
        assertThat(leg.asset().code()).isEqualTo("MXRF11");
        assertThat(leg.type()).isEqualTo(TradeType.BUY);
        assertThat(leg.unitPrice()).isEqualTo(Money.brl("9.64"));
    }

    @Test
    void shouldReportWhatItDoesNotRecord() {
        var statement = read(
                row("Credito", "08/10/2026", "Atualização", "AUAU3 - UNIÃO PET PARTICIPAÇÕES S.A.", 100, "-", "-"),
                row("Debito", "17/09/2026", "Transferência", "HGLG11 - PÁTRIA LOG", 1, "-", "-"),
                row("Credito", "06/07/2026", "Restituição de Capital", "ENJU3 - ENJOEI S.A.", 100, 0.202, 20.16),
                row("Credito", "06/01/2026", "Incorporação", "AUAU3 - UNIÃO PET PARTICIPAÇÕES S.A.", 100, "-", "-"),
                row("Credito", "25/03/2026", "Leilão de Fração", "MGLU3 - MAGAZINE LUIZA S.A.", 0.5, 9.147, 4.57));

        assertThat(statement.entries()).isEmpty();
        assertThat(statement.issues()).extracting(ImportIssue::code).containsExactlyInAnyOrder(
                ImportIssue.Code.INFORMATIONAL, ImportIssue.Code.INFORMATIONAL, ImportIssue.Code.UNSUPPORTED,
                ImportIssue.Code.MERGER_REQUIRED, ImportIssue.Code.CORPORATE_EVENT_CASH);
        assertThat(statement.issues()).extracting(ImportIssue::line).contains(2, 6);
    }

    @Test
    void shouldReadFixedIncomeApplicationsAndRedemptionsAsTradesOfUnits() {
        var statement = read(
                row("Debito", "30/06/2026", "RESGATE ANTECIPADO/", "CDB - CDB6255F9RH - BANCO INTER S/A", 3721,
                        0.01156031, 43.02),
                row("Credito", "10/06/2025", "APLICAÇÃO", "CDB - CDB6255F9RH", 10000, 0.01, 100));

        var legs = statement.entries().stream()
                .map(entry -> ((StatementEntry.TradeGroup) entry).legs().getFirst())
                .toList();
        assertThat(legs).extracting(leg -> leg.type()).containsExactly(TradeType.BUY, TradeType.SELL);
        assertThat(legs).extracting(leg -> leg.asset().code()).containsOnly("CDB6255F9RH");
        assertThat(legs.getFirst().asset().assetClass()).isEqualTo(AssetClass.FIXED_INCOME);
        assertThat(legs.getFirst().asset().name()).isEqualTo("CDB CORRETORA EXEMPLO S.A. (CDB6255F9RH)");
        assertThat(legs.getFirst().quantity()).isEqualTo(Quantity.of(10000));
        assertThat(legs.getFirst().unitPrice()).isEqualTo(Money.brl("0.01"));
        assertThat(legs.getLast().unitPrice()).isEqualTo(Money.brl("0.01156031"));
    }

    @Test
    void shouldGiveIdenticalRowsDifferentButStableFingerprints() {
        var rows = List.of(
                row("Credito", "09/01/2026", "Compra", "Tesouro IPCA+ 2029", 0.01, 3575.13, 35.75),
                row("Credito", "09/01/2026", "Compra", "Tesouro IPCA+ 2029", 0.01, 3575.13, 35.75));

        var first = read(rows.toArray(Object[][]::new));
        var again = read(rows.toArray(Object[][]::new));

        var fingerprints = first.entries().stream().map(StatementEntry::fingerprint).toList();
        assertThat(fingerprints).doesNotHaveDuplicates();
        assertThat(again.entries()).extracting(StatementEntry::fingerprint).isEqualTo(fingerprints);
    }

    @Test
    void shouldRejectAFileWithoutTheExpectedColumns() {
        List<Object[]> otherSheet = List.<Object[]>of(new Object[] {"Ativo", "Valor"});
        assertThatThrownBy(() -> reader.read("other.xlsx", workbook(otherSheet)))
                .isInstanceOf(UnreadableStatementException.class);
        assertThatThrownBy(() -> reader.read("not-excel.xlsx", "a,b,c".getBytes()))
                .isInstanceOf(UnreadableStatementException.class);
    }

    @Test
    void shouldBuildTesouroCodesForEveryBondType() {
        assertThat(B3MovementReader.treasuryCode("Tesouro Selic 2029")).isEqualTo("TESOURO-SELIC-2029");
        assertThat(B3MovementReader.treasuryCode("Tesouro Prefixado com Juros Semestrais 2031"))
                .isEqualTo("TESOURO-PRE-JS-2031");
        assertThat(B3MovementReader.treasuryCode("Tesouro Renda+ Aposentadoria Extra 2065"))
                .isEqualTo("TESOURO-RENDA-2065");
        assertThat(B3MovementReader.treasuryCode("Tesouro Educa+ 2030")).isEqualTo("TESOURO-EDUCA-2030");
    }

    private Statement read(Object[]... rows) {
        var all = new java.util.ArrayList<Object[]>();
        all.add(HEADER.split("\\|"));
        all.addAll(List.of(rows));
        return reader.read("movimentacao.xlsx", workbook(all));
    }

    private static Object[] row(String direction, String date, String movement, String product, Object quantity,
            Object price, Object value) {
        return new Object[] {direction, date, movement, product, BROKER, quantity, price, value};
    }

    /** Same cell types as the real export: text for dates and dashes, numbers for quantities and values. */
    private static byte[] workbook(List<Object[]> rows) {
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Movimentação");
            for (int r = 0; r < rows.size(); r++) {
                var row = sheet.createRow(r);
                for (int c = 0; c < rows.get(r).length; c++) {
                    var value = rows.get(r)[c];
                    if (value instanceof Number number) {
                        row.createCell(c).setCellValue(number.doubleValue());
                    } else {
                        row.createCell(c).setCellValue(value.toString());
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
