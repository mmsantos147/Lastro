package io.matheusms.lastro.adapter.in.rest.portfolio;

import static io.matheusms.lastro.adapter.in.rest.Ratios.rounded;

import io.matheusms.lastro.adapter.in.rest.CurrentUser;
import io.matheusms.lastro.application.PerformanceService;
import io.matheusms.lastro.application.PerformanceService.PerformancePoint;
import io.matheusms.lastro.application.PerformanceService.Summary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio")
class PortfolioController {

    /** Long histories are sampled weekly so the chart stays light; the last day is always kept. */
    private static final int MAX_DAILY_POINTS = 400;

    private final PerformanceService performance;

    PortfolioController(PerformanceService performance) {
        this.performance = performance;
    }

    /** Market value, net contributions and returns vs CDI and Ibovespa. {@code null} body fields when empty. */
    @GetMapping("/summary")
    SummaryResponse summary(@CurrentUser Long userId) {
        return SummaryResponse.from(performance.performance(userId).summary());
    }

    @GetMapping("/history")
    List<HistoryPoint> history(@CurrentUser Long userId) {
        var points = performance.performance(userId).points();
        return sample(points).stream().map(HistoryPoint::from).toList();
    }

    private static List<PerformancePoint> sample(List<PerformancePoint> points) {
        if (points.size() <= MAX_DAILY_POINTS) {
            return points;
        }
        var sampled = new ArrayList<PerformancePoint>();
        for (int i = 0; i < points.size() - 1; i += 7) {
            sampled.add(points.get(i));
        }
        sampled.add(points.getLast());
        return sampled;
    }

    /** Returns are ratios (0.1 = 10%). */
    record SummaryResponse(
            LocalDate startDate,
            BigDecimal marketValue,
            BigDecimal invested,
            BigDecimal returnSinceStart,
            BigDecimal cdiSinceStart,
            BigDecimal ibovSinceStart,
            BigDecimal return12Months,
            BigDecimal cdi12Months,
            BigDecimal ibov12Months) {

        static SummaryResponse from(Summary summary) {
            if (summary == null) {
                return new SummaryResponse(null, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, null, null, null);
            }
            return new SummaryResponse(summary.startDate(), summary.marketValue().amount(), summary.invested().amount(),
                    rounded(summary.returnSinceStart()), rounded(summary.cdiSinceStart()),
                    rounded(summary.ibovSinceStart()), rounded(summary.return12Months()),
                    rounded(summary.cdi12Months()), rounded(summary.ibov12Months()));
        }
    }

    /** Indices are growth factors since the first day (1.1 = +10%). */
    record HistoryPoint(
            LocalDate date, BigDecimal marketValue, BigDecimal invested, BigDecimal portfolioIndex,
            BigDecimal cdiIndex, BigDecimal ibovIndex) {

        static HistoryPoint from(PerformancePoint point) {
            return new HistoryPoint(point.date(), point.marketValue().amount(), point.invested().amount(),
                    rounded(point.portfolioIndex()), rounded(point.cdiIndex()), rounded(point.ibovIndex()));
        }
    }
}
