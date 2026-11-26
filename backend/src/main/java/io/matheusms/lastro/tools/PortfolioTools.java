package io.matheusms.lastro.tools;

import io.matheusms.lastro.application.AllocationService;
import io.matheusms.lastro.application.AnalysisService;
import io.matheusms.lastro.application.AssetService;
import io.matheusms.lastro.application.ContributionService;
import io.matheusms.lastro.application.IncomeService;
import io.matheusms.lastro.application.PerformanceService;
import io.matheusms.lastro.application.PositionService;
import io.matheusms.lastro.domain.model.Asset;
import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.SuggestionReason;
import io.matheusms.lastro.domain.model.SuggestionSource;
import io.matheusms.lastro.domain.service.AllocationAnalyzer.Status;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Implementation of the portfolio tools (see {@link ToolDefinitions}). Read-only except {@code save_analysis}.
 * Results never carry personal data (documents, account numbers, keys): only portfolio figures.
 */
@Component
public class PortfolioTools {

    private static final int RATIO_SCALE = 6;

    private final PerformanceService performance;
    private final PositionService positions;
    private final AllocationService allocation;
    private final ContributionService contributions;
    private final IncomeService income;
    private final AnalysisService analyses;
    private final AssetService assets;
    private final Clock clock;
    private final String publicUrl;

    PortfolioTools(PerformanceService performance, PositionService positions, AllocationService allocation,
            ContributionService contributions, IncomeService income, AnalysisService analyses, AssetService assets,
            Clock clock, @Value("${lastro.public-url}") String publicUrl) {
        this.performance = performance;
        this.positions = positions;
        this.allocation = allocation;
        this.contributions = contributions;
        this.income = income;
        this.analyses = analyses;
        this.assets = assets;
        this.clock = clock;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    public PortfolioSummary portfolioSummary(Long userId) {
        var summary = performance.performance(userId).summary();
        var today = LocalDate.now(clock);
        if (summary == null) {
            return new PortfolioSummary(today, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null,
                    null, null, null, "The portfolio has no trades yet");
        }
        var gain = summary.marketValue().minus(summary.invested());
        return new PortfolioSummary(today, summary.startDate(), cents(summary.marketValue()), cents(summary.invested()),
                cents(gain), ratio(summary.returnSinceStart()), ratio(summary.cdiSinceStart()),
                ratio(summary.ibovSinceStart()), ratio(summary.return12Months()), ratio(summary.cdi12Months()),
                ratio(summary.ibov12Months()),
                summary.ibovSinceStart() == null ? "Ibovespa data unavailable" : null);
    }

    public List<PositionView> listPositions(Long userId, AssetClass assetClass) {
        var open = positions.openPositions(userId);
        var total = open.stream().map(PositionService.AssetPosition::marketValue)
                .reduce(Money.zero(Currency.BRL), Money::plus);
        return open.stream()
                .filter(position -> assetClass == null || position.asset().assetClass() == assetClass)
                .map(position -> {
                    var cost = position.position().totalCost();
                    var value = position.marketValue();
                    return new PositionView(position.asset().code(), position.asset().name(),
                            position.asset().assetClass(), position.position().quantity().value().stripTrailingZeros(),
                            position.position().averageCost().amount().setScale(4, RoundingMode.HALF_EVEN),
                            position.hasQuote() ? position.price().amount().setScale(4, RoundingMode.HALF_EVEN) : null,
                            position.priceDate(), cents(cost), cents(value),
                            position.hasQuote() ? cents(value.minus(cost)) : null,
                            position.hasQuote() && !cost.isZero() ? ratio(divide(value, cost).subtract(BigDecimal.ONE)) : null,
                            total.isZero() ? BigDecimal.ZERO : ratio(divide(value, total)));
                })
                .toList();
    }

    public AllocationView allocationVsTarget(Long userId) {
        var result = allocation.allocationVsTarget(userId);
        var codes = assetCodes();
        var buckets = result.buckets().stream()
                .map(bucket -> new BucketView(bucket.assetClass(),
                        bucket.assetId() == null ? null : codes.get(bucket.assetId()), cents(bucket.value()),
                        bucket.currentPercentage(), bucket.targetPercentage(), bucket.deviation(),
                        bucket.target() == null ? null : bucket.target().minTolerance(),
                        bucket.target() == null ? null : bucket.target().maxTolerance(), bucket.status()))
                .toList();
        var strategy = result.strategy();
        return new AllocationView(strategy != null, strategy == null ? null : strategy.name(), cents(result.total()),
                buckets, strategy == null ? "There is no active strategy: the target allocation is not defined" : null);
    }

    public SuggestionView suggestContribution(Long userId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be a positive value in BRL");
        }
        var suggestion = contributions.suggest(userId, new Money(amount, Currency.BRL), SuggestionSource.MCP);
        if (suggestion.isEmpty()) {
            return new SuggestionView(null, amount.setScale(2, RoundingMode.HALF_EVEN), List.of(),
                    "NO_ACTIVE_STRATEGY: there is no target allocation, so the rules engine cannot suggest a "
                            + "contribution. Help the user define a strategy first.");
        }
        var codes = assetCodes();
        var items = suggestion.get().items().stream()
                .map(item -> new SuggestionItemView(item.assetClass(),
                        item.assetId() == null ? null : codes.get(item.assetId()), cents(item.suggestedAmount()),
                        item.reason(), item.currentPercentage(), item.targetPercentage()))
                .toList();
        return new SuggestionView(suggestion.get().id(), cents(suggestion.get().amount()), items, null);
    }

    public IncomeHistory distributionHistory(Long userId, Integer months) {
        int period = months == null ? 12 : months;
        if (period < 1 || period > 120) {
            throw new IllegalArgumentException("months must be between 1 and 120");
        }
        var result = income.income(userId, period);
        return new IncomeHistory(
                result.months().stream()
                        .map(month -> new MonthIncome(month.month().toString(), cents(month.net()),
                                cents(month.movingAverage())))
                        .toList(),
                cents(result.lastTwelveMonths()), ratio(result.yieldOnCost()),
                result.byAsset().stream()
                        .map(asset -> new AssetIncomeView(asset.asset().code(), cents(asset.lastTwelveMonths()),
                                ratio(asset.yieldOnCost())))
                        .toList());
    }

    public SavedAnalysisView saveAnalysis(Long userId, String title, String text, BigDecimal amount, Long suggestionId) {
        var saved = analyses.save(userId, title, text, amount == null ? null : new Money(amount, Currency.BRL),
                suggestionId);
        var id = saved.analysis().id();
        return new SavedAnalysisView(id, publicUrl + "/analises/" + id, saved.analysis().contributionSuggestionId(),
                saved.warning().orElse(null));
    }

    private Map<Long, String> assetCodes() {
        return assets.list().stream().collect(Collectors.toMap(Asset::id, Asset::code, (first, second) -> first));
    }

    private static BigDecimal cents(Money money) {
        return money.roundedToCents().amount().setScale(2, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal ratio(BigDecimal value) {
        return value == null ? null : value.setScale(RATIO_SCALE, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal divide(Money dividend, Money divisor) {
        return dividend.amount().divide(divisor.amount(), MathContext.DECIMAL64);
    }

    /** Returns are ratios (0.1 = 10%); "last year" means the last 12 months. */
    public record PortfolioSummary(
            LocalDate asOf, LocalDate startDate, BigDecimal marketValueBrl, BigDecimal netInvestedBrl,
            BigDecimal unrealizedGainBrl, BigDecimal returnSinceStart, BigDecimal cdiSinceStart,
            BigDecimal ibovespaSinceStart, BigDecimal returnLastYear, BigDecimal cdiLastYear,
            BigDecimal ibovespaLastYear, String note) {
    }

    /** Amounts in BRL; resultRatio and weight are ratios. */
    public record PositionView(
            String code, String name, AssetClass assetClass, BigDecimal quantity, BigDecimal averageCostBrl,
            BigDecimal priceBrl, LocalDate priceDate, BigDecimal totalCostBrl, BigDecimal marketValueBrl,
            BigDecimal resultBrl, BigDecimal resultRatio, BigDecimal weight) {
    }

    public record AllocationView(
            boolean hasStrategy, String strategyName, BigDecimal totalBrl, List<BucketView> targets, String note) {
    }

    /** Percentages in percent (25 = 25%); deviation and tolerances in percentage points. */
    public record BucketView(
            AssetClass assetClass, String assetCode, BigDecimal valueBrl, BigDecimal currentPercentage,
            BigDecimal targetPercentage, BigDecimal deviationPoints, BigDecimal toleranceBelowPoints,
            BigDecimal toleranceAbovePoints, Status status) {
    }

    /** @param warning set (and no items) when there is no active strategy */
    public record SuggestionView(
            Long suggestionId, BigDecimal amountBrl, List<SuggestionItemView> items, String warning) {
    }

    /** {@code assetCode} is null when the user must choose an asset of the class. */
    public record SuggestionItemView(
            AssetClass assetClass, String assetCode, BigDecimal amountBrl, SuggestionReason reason,
            BigDecimal currentPercentage, BigDecimal targetPercentage) {
    }

    /** "Last year" means the last 12 months; the moving average covers the 12 months ending in each month. */
    public record IncomeHistory(
            List<MonthIncome> months, BigDecimal lastYearBrl, BigDecimal yieldOnCost,
            List<AssetIncomeView> lastYearByAsset) {
    }

    /** @param month {@code yyyy-MM} */
    public record MonthIncome(String month, BigDecimal netBrl, BigDecimal movingAverageLastYearBrl) {
    }

    public record AssetIncomeView(String code, BigDecimal netBrl, BigDecimal yieldOnCost) {
    }

    /** @param suggestionId the linked suggestion, or null when it could not be linked (see warning) */
    public record SavedAnalysisView(Long id, String url, Long suggestionId, String warning) {
    }
}
