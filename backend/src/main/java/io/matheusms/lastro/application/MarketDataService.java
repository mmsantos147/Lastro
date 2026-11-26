package io.matheusms.lastro.application;

import io.matheusms.lastro.domain.model.Asset;
import io.matheusms.lastro.domain.model.IndicatorType;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quote;
import io.matheusms.lastro.domain.model.QuoteSource;
import io.matheusms.lastro.domain.port.AssetRepository;
import io.matheusms.lastro.domain.port.EconomicIndicatorProvider;
import io.matheusms.lastro.domain.port.EconomicIndicatorRepository;
import io.matheusms.lastro.domain.port.QuoteProvider;
import io.matheusms.lastro.domain.port.QuoteRepository;
import io.matheusms.lastro.domain.port.TradeRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps quotes and indicators up to date. Each refresh fetches only what is missing: from the day after the latest
 * stored value, or from the first trade when nothing is stored yet. A failing source never stops the others.
 */
@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);
    /** Without any trade, indicators start this far back (enough for the 12-month comparisons). */
    private static final int DEFAULT_HISTORY_DAYS = 400;
    /** Benchmarks need a value on or before the first day of the portfolio (weekends, holidays). */
    private static final int BENCHMARK_MARGIN_DAYS = 10;

    private final AssetRepository assets;
    private final TradeRepository trades;
    private final QuoteRepository quotes;
    private final EconomicIndicatorRepository indicators;
    private final List<QuoteProvider> quoteProviders;
    private final List<EconomicIndicatorProvider> indicatorProviders;
    private final FixedIncomeService fixedIncome;
    private final Clock clock;
    private final AtomicBoolean refreshing = new AtomicBoolean();

    MarketDataService(AssetRepository assets, TradeRepository trades, QuoteRepository quotes,
            EconomicIndicatorRepository indicators, List<QuoteProvider> quoteProviders,
            List<EconomicIndicatorProvider> indicatorProviders, FixedIncomeService fixedIncome, Clock clock) {
        this.fixedIncome = fixedIncome;
        this.assets = assets;
        this.trades = trades;
        this.quotes = quotes;
        this.indicators = indicators;
        this.quoteProviders = quoteProviders;
        this.indicatorProviders = indicatorProviders;
        this.clock = clock;
    }

    /**
     * @return what was updated and what failed
     * @throws ConflictException if a refresh is already running
     */
    public RefreshResult refresh() {
        if (!refreshing.compareAndSet(false, true)) {
            throw new ConflictException("A market data refresh is already running");
        }
        try {
            var failures = new ArrayList<String>();
            int quoteCount = refreshQuotes(failures);
            int indicatorCount = refreshIndicators(failures);
            // Fixed income curves are computed from the indicators just refreshed
            quoteCount += fixedIncome.refreshCurves(failures);
            log.info("Market data refreshed: {} quotes, {} indicator values, {} failures",
                    quoteCount, indicatorCount, failures.size());
            return new RefreshResult(quoteCount, indicatorCount, failures);
        } finally {
            refreshing.set(false);
        }
    }

    private int refreshQuotes(List<String> failures) {
        var today = LocalDate.now(clock);
        int count = 0;
        for (Asset asset : assets.findAll()) {
            var provider = quoteProviders.stream().filter(candidate -> candidate.supports(asset)).findFirst();
            if (!asset.active() || provider.isEmpty()) {
                continue;
            }
            var wantedStart = trades.findFirstTradeDate(asset.id()).orElse(today.minusDays(7));
            var ranges = missingRanges(quotes.findEarliestDate(asset.id()), quotes.findLatestDate(asset.id()),
                    wantedStart, today);
            try {
                for (DateRange range : ranges) {
                    var fetched = provider.get().dailyCloses(asset, range.from(), range.to());
                    quotes.upsert(fetched);
                    count += fetched.size();
                }
            } catch (RuntimeException exception) {
                log.warn("Could not refresh quotes of {}: {}", asset.code(), exception.getMessage());
                failures.add(asset.code());
            }
        }
        return count;
    }

    private int refreshIndicators(List<String> failures) {
        var today = LocalDate.now(clock);
        var defaultStart = trades.findFirstTradeDate()
                .map(first -> first.minusDays(BENCHMARK_MARGIN_DAYS))
                .orElse(today.minusDays(DEFAULT_HISTORY_DAYS));
        int count = 0;
        for (IndicatorType type : IndicatorType.values()) {
            var provider = indicatorProviders.stream().filter(candidate -> candidate.supports(type)).findFirst();
            if (provider.isEmpty()) {
                continue;
            }
            var ranges = missingRanges(indicators.findEarliestDate(type), indicators.findLatestDate(type),
                    defaultStart, today);
            try {
                for (DateRange range : ranges) {
                    var fetched = provider.get().fetch(type, range.from(), range.to());
                    indicators.upsert(fetched);
                    count += fetched.size();
                }
            } catch (RuntimeException exception) {
                log.warn("Could not refresh indicator {}: {}", type, exception.getMessage());
                failures.add(type.name());
            }
        }
        return count;
    }

    /**
     * Periods still missing: before the earliest stored value (a trade older than the data was recorded) and after
     * the latest one. Sources that do not go that far back simply return less.
     */
    static List<DateRange> missingRanges(
            Optional<LocalDate> earliest, Optional<LocalDate> latest, LocalDate wantedStart, LocalDate today) {
        if (earliest.isEmpty() || latest.isEmpty()) {
            return wantedStart.isAfter(today) ? List.of() : List.of(new DateRange(wantedStart, today));
        }
        var ranges = new ArrayList<DateRange>();
        if (wantedStart.isBefore(earliest.get())) {
            ranges.add(new DateRange(wantedStart, earliest.get().minusDays(1)));
        }
        if (latest.get().isBefore(today)) {
            ranges.add(new DateRange(latest.get().plusDays(1), today));
        }
        return ranges;
    }

    record DateRange(LocalDate from, LocalDate to) {
    }

    /** Quote typed by the user, for assets without an automatic source (e.g. Tesouro Direto). */
    @Transactional
    public Quote saveManualQuote(Long assetId, LocalDate date, BigDecimal price) {
        var asset = assets.findById(assetId).orElseThrow(() -> new NotFoundException("Asset", assetId));
        var quote = new Quote(assetId, date, new Money(price, asset.quoteCurrency()), QuoteSource.MANUAL);
        quotes.upsert(List.of(quote));
        return quote;
    }

    @Transactional(readOnly = true)
    public List<Quote> quotesOf(Long assetId) {
        return quotes.findByAsset(assetId);
    }

    @Transactional
    public void deleteQuote(Long assetId, LocalDate date) {
        quotes.delete(assetId, date);
    }

    /** Latest stored date of each indicator; empty when never loaded. */
    @Transactional(readOnly = true)
    public List<IndicatorStatus> indicatorStatus() {
        return Arrays.stream(IndicatorType.values())
                .map(type -> new IndicatorStatus(type, indicators.findLatestDate(type)))
                .toList();
    }

    public record RefreshResult(int quotes, int indicatorValues, List<String> failures) {
    }

    public record IndicatorStatus(IndicatorType type, Optional<LocalDate> latestDate) {
    }
}
