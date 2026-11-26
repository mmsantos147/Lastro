package io.matheusms.lastro.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Conditions of a private fixed income security (CDB, LCI, LCA), which give its value on the curve ("marcação na
 * curva"): the price paid accrued by the agreed yield, before income tax. Files exported by B3 do not carry them, so
 * the user types them from the bank's statement.
 *
 * @param assetId the {@link AssetClass#FIXED_INCOME} asset these terms belong to
 * @param issuer bank that issued the security; {@code null} when unknown
 * @param indexerPercentage for {@link FixedIncomeIndexer#CDI} and {@link FixedIncomeIndexer#SELIC}, the percentage of
 *     the index ({@code 110} for "110% do CDI"); {@code null} for the other indexers
 * @param rate annual rate in percent: the fixed rate ({@link FixedIncomeIndexer#PREFIXED}), the real rate over IPCA, or
 *     the spread over CDI/Selic ({@code 0} when there is none)
 * @param maturityDate date the security pays back; the curve stops there. {@code null} when unknown
 * @param dailyLiquidity whether it can be redeemed any day before maturity
 */
public record FixedIncomeTerms(
        Long assetId,
        String issuer,
        FixedIncomeIndexer indexer,
        BigDecimal indexerPercentage,
        BigDecimal rate,
        LocalDate maturityDate,
        boolean dailyLiquidity) {

    public FixedIncomeTerms {
        Objects.requireNonNull(assetId, "assetId must not be null");
        Objects.requireNonNull(indexer, "indexer must not be null");
        Objects.requireNonNull(rate, "rate must not be null");
        if (rate.signum() < 0) {
            throw new IllegalArgumentException("Rate must not be negative");
        }
        boolean followsAnIndex = indexer == FixedIncomeIndexer.CDI || indexer == FixedIncomeIndexer.SELIC;
        if (followsAnIndex && (indexerPercentage == null || indexerPercentage.signum() <= 0)) {
            throw new IllegalArgumentException(indexer + " securities need a positive percentage of the index");
        }
        if (!followsAnIndex && indexerPercentage != null) {
            throw new IllegalArgumentException("Only CDI and Selic securities have a percentage of the index");
        }
        if (issuer != null && issuer.isBlank()) {
            issuer = null;
        }
    }
}
