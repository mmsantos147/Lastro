package io.matheusms.lastro.application;

import io.matheusms.lastro.domain.model.IndicatorType;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.port.EconomicIndicatorRepository;
import io.matheusms.lastro.domain.service.BenchmarkIndex;
import io.matheusms.lastro.domain.service.BrlConverter;
import io.matheusms.lastro.domain.service.PortfolioTimeline;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Portfolio value over time and its time-weighted return compared with CDI and Ibovespa over the same days.
 */
@Service
public class PerformanceService {

    private static final MathContext PRECISION = MathContext.DECIMAL64;

    private final PortfolioLoader loader;
    private final EconomicIndicatorRepository indicators;
    private final Clock clock;
    private final BrlConverter converter = new BrlConverter();
    private final PortfolioTimeline timeline = new PortfolioTimeline();

    PerformanceService(PortfolioLoader loader, EconomicIndicatorRepository indicators, Clock clock) {
        this.loader = loader;
        this.indicators = indicators;
        this.clock = clock;
    }

    /**
     * @return one point per day since the first trade; empty when there are no trades
     * @throws io.matheusms.lastro.domain.service.MissingMarketDataException if conversion data is missing
     */
    @Transactional(readOnly = true)
    public Performance performance(Long userId) {
        var today = LocalDate.now(clock);
        var portfolio = loader.load(userId);
        var prices = loader.marketData(portfolio, today);
        var converted = converter.convert(portfolio.histories(), portfolio.distributions(), prices);
        var days = timeline.build(converted.histories(), converted.cashFlows(), prices, today);
        if (days.isEmpty()) {
            return Performance.EMPTY;
        }
        var start = days.getFirst().date();
        var cdi = BenchmarkIndex.compoundDailyRates(indicators.findByTypeBetween(IndicatorType.CDI, start, today), start);
        var ibov = BenchmarkIndex.relativeToStart(
                indicators.findByTypeBetween(IndicatorType.IBOV, start.minusDays(15), today), start);
        var points = days.stream()
                .map(day -> new PerformancePoint(day.date(), day.marketValue(), day.invested(), day.returnIndex(),
                        BenchmarkIndex.on(cdi, day.date()).orElse(BigDecimal.ONE),
                        ibov.flatMap(series -> BenchmarkIndex.on(series, day.date())).orElse(null)))
                .toList();
        return new Performance(points, summary(points, today, ibov.isPresent()));
    }

    private static Summary summary(List<PerformancePoint> points, LocalDate today, boolean hasIbov) {
        var last = points.getLast();
        var yearAgo = pointOn(points, today.minusYears(1));
        return new Summary(
                points.getFirst().date(),
                last.marketValue(),
                last.invested(),
                last.portfolioIndex().subtract(BigDecimal.ONE),
                last.cdiIndex().subtract(BigDecimal.ONE),
                hasIbov && last.ibovIndex() != null ? last.ibovIndex().subtract(BigDecimal.ONE) : null,
                yearAgo.map(base -> growth(base.portfolioIndex(), last.portfolioIndex())).orElse(null),
                yearAgo.map(base -> growth(base.cdiIndex(), last.cdiIndex())).orElse(null),
                yearAgo.filter(base -> base.ibovIndex() != null && last.ibovIndex() != null)
                        .map(base -> growth(base.ibovIndex(), last.ibovIndex())).orElse(null));
    }

    /** The point of the date, when the series reaches that far back. */
    private static Optional<PerformancePoint> pointOn(List<PerformancePoint> points, LocalDate date) {
        if (points.getFirst().date().isAfter(date)) {
            return Optional.empty();
        }
        return points.stream().filter(point -> !point.date().isAfter(date)).reduce((first, second) -> second);
    }

    private static BigDecimal growth(BigDecimal from, BigDecimal to) {
        return from.signum() == 0 ? BigDecimal.ZERO : to.divide(from, PRECISION).subtract(BigDecimal.ONE);
    }

    /**
     * @param invested cumulative contributions minus withdrawals (sale proceeds, cash distributions)
     * @param portfolioIndex cumulative time-weighted return as a growth factor (1.10 = +10%)
     * @param cdiIndex CDI compounded since the first day, as a growth factor
     * @param ibovIndex Ibovespa relative to the first day; {@code null} when not available
     */
    public record PerformancePoint(
            LocalDate date, Money marketValue, Money invested, BigDecimal portfolioIndex, BigDecimal cdiIndex,
            BigDecimal ibovIndex) {
    }

    /**
     * Returns are ratios (0.1 = 10%); the 12-month ones are {@code null} when the portfolio is younger than that,
     * and Ibovespa ones when its data is unavailable.
     */
    public record Summary(
            LocalDate startDate,
            Money marketValue,
            Money invested,
            BigDecimal returnSinceStart,
            BigDecimal cdiSinceStart,
            BigDecimal ibovSinceStart,
            BigDecimal return12Months,
            BigDecimal cdi12Months,
            BigDecimal ibov12Months) {
    }

    /** @param summary {@code null} when there are no trades */
    public record Performance(List<PerformancePoint> points, Summary summary) {

        static final Performance EMPTY = new Performance(List.of(), null);
    }
}
