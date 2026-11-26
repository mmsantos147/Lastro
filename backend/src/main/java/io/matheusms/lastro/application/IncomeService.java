package io.matheusms.lastro.application;

import io.matheusms.lastro.domain.model.Asset;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.port.AssetRepository;
import io.matheusms.lastro.domain.service.IncomeReport;
import io.matheusms.lastro.domain.service.IncomeReport.MonthlyIncome;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Passive income: monthly distributions and yield on cost. */
@Service
public class IncomeService {

    private final PortfolioLoader loader;
    private final PositionService positions;
    private final AssetRepository assets;
    private final Clock clock;
    private final IncomeReport report = new IncomeReport();

    IncomeService(PortfolioLoader loader, PositionService positions, AssetRepository assets, Clock clock) {
        this.loader = loader;
        this.positions = positions;
        this.assets = assets;
        this.clock = clock;
    }

    /**
     * @param months how many months of history, ending with the current month
     */
    @Transactional(readOnly = true)
    public Income income(Long userId, int months) {
        var today = LocalDate.now(clock);
        var portfolio = loader.load(userId);
        var result = report.build(portfolio.distributions(), loader.marketData(portfolio, today), today, months);
        var open = positions.openPositions(userId);
        var totalCost = open.stream().map(position -> position.position().totalCost())
                .reduce(Money.zero(Currency.BRL), Money::plus);
        var byAsset = result.lastTwelveMonthsByAsset().entrySet().stream()
                .map(entry -> {
                    var cost = open.stream()
                            .filter(position -> position.asset().id().equals(entry.getKey()))
                            .map(position -> position.position().totalCost())
                            .findFirst();
                    return new AssetIncome(asset(entry.getKey()), entry.getValue(),
                            cost.map(value -> ratio(entry.getValue(), value)).orElse(null));
                })
                .sorted(Comparator.comparing((AssetIncome income) -> income.lastTwelveMonths().amount()).reversed())
                .toList();
        return new Income(result.months(), result.lastTwelveMonths(), ratio(result.lastTwelveMonths(), totalCost),
                byAsset);
    }

    private Asset asset(Long id) {
        return assets.findById(id).orElseThrow(() -> new NotFoundException("Asset", id));
    }

    /** {@code null} when there is no cost to divide by. */
    private static BigDecimal ratio(Money income, Money cost) {
        return cost.isZero() ? null : income.amount().divide(cost.amount(), MathContext.DECIMAL64);
    }

    /**
     * @param yieldOnCost income of the last 12 months over the current total cost of the open positions
     * @param byAsset income of the last 12 months per asset, largest first
     */
    public record Income(
            List<MonthlyIncome> months, Money lastTwelveMonths, BigDecimal yieldOnCost, List<AssetIncome> byAsset) {
    }

    /** @param yieldOnCost {@code null} when the position is closed */
    public record AssetIncome(Asset asset, Money lastTwelveMonths, BigDecimal yieldOnCost) {
    }
}
