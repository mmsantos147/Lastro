package io.matheusms.lastro.application;

import io.matheusms.lastro.domain.model.Asset;
import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.EconomicIndicator;
import io.matheusms.lastro.domain.model.FixedIncomeIndexer;
import io.matheusms.lastro.domain.model.FixedIncomeTerms;
import io.matheusms.lastro.domain.model.IndicatorType;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quote;
import io.matheusms.lastro.domain.model.QuoteSource;
import io.matheusms.lastro.domain.port.AssetRepository;
import io.matheusms.lastro.domain.port.EconomicIndicatorRepository;
import io.matheusms.lastro.domain.port.FixedIncomeTermsRepository;
import io.matheusms.lastro.domain.port.QuoteRepository;
import io.matheusms.lastro.domain.port.TradeRepository;
import io.matheusms.lastro.domain.service.FixedIncomeCurve;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Terms of private fixed income securities and their value on the curve, stored as {@link QuoteSource#CURVE} quotes so
 * every valuation uses them like any other price (ADR 0010). The curve starts at the asset's first trade (date and
 * unit price): each B3 application of a CDB has its own code, so one asset is one application.
 */
@Service
public class FixedIncomeService {

    private static final Logger log = LoggerFactory.getLogger(FixedIncomeService.class);

    private final AssetRepository assets;
    private final FixedIncomeTermsRepository terms;
    private final TradeRepository trades;
    private final QuoteRepository quotes;
    private final EconomicIndicatorRepository indicators;
    private final Clock clock;
    private final FixedIncomeCurve curve = new FixedIncomeCurve();

    FixedIncomeService(AssetRepository assets, FixedIncomeTermsRepository terms, TradeRepository trades,
            QuoteRepository quotes, EconomicIndicatorRepository indicators, Clock clock) {
        this.assets = assets;
        this.terms = terms;
        this.trades = trades;
        this.quotes = quotes;
        this.indicators = indicators;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FixedIncomeTerms> list() {
        return terms.findAll();
    }

    /** Saves the terms and recomputes the asset's curve right away. */
    @Transactional
    public FixedIncomeTerms save(FixedIncomeTerms changes) {
        var asset = assets.findById(changes.assetId())
                .orElseThrow(() -> new NotFoundException("Asset", changes.assetId()));
        if (asset.assetClass() != AssetClass.FIXED_INCOME) {
            throw new IllegalArgumentException("Terms apply only to FIXED_INCOME assets, not " + asset.assetClass());
        }
        var saved = terms.save(changes);
        refreshCurve(asset, saved);
        return saved;
    }

    /**
     * Recomputes the curve of every active fixed income asset with terms. Called by the market data refresh, after
     * the indicators.
     *
     * @return number of quotes written; codes that failed are added to {@code failures}
     */
    int refreshCurves(List<String> failures) {
        int count = 0;
        for (FixedIncomeTerms security : terms.findAll()) {
            var asset = assets.findById(security.assetId()).orElse(null);
            if (asset == null || !asset.active()) {
                continue;
            }
            try {
                count += refreshCurve(asset, security);
            } catch (RuntimeException exception) {
                log.warn("Could not compute the curve of {}: {}", asset.code(), exception.getMessage());
                failures.add(asset.code());
            }
        }
        return count;
    }

    /** Manual quotes are kept: they replace the curve on their dates. */
    private int refreshCurve(Asset asset, FixedIncomeTerms security) {
        var first = trades.findFirstTrade(asset.id()).orElse(null);
        if (first == null || first.priceCurrency() != Currency.BRL) {
            return 0;
        }
        var today = LocalDate.now(clock);
        var dailyType = security.indexer() == FixedIncomeIndexer.SELIC ? IndicatorType.SELIC : IndicatorType.CDI;
        var daily = indicators.findByTypeBetween(dailyType, first.date(), today);
        var ipca = security.indexer() == FixedIncomeIndexer.IPCA
                ? indicators.findByTypeBetween(IndicatorType.IPCA, first.date().minusYears(1), today)
                : List.<EconomicIndicator>of();
        var manualDates = quotes.findByAsset(asset.id()).stream()
                .filter(quote -> quote.source() == QuoteSource.MANUAL)
                .map(Quote::date)
                .collect(Collectors.toSet());
        var values = curve.unitValues(security, first.date(), first.unitPrice().amount(), daily, ipca, today);
        var curveQuotes = values.entrySet().stream()
                .filter(entry -> !manualDates.contains(entry.getKey()))
                .map(entry -> new Quote(asset.id(), entry.getKey(), new Money(entry.getValue(), Currency.BRL),
                        QuoteSource.CURVE))
                .toList();
        quotes.upsert(curveQuotes);
        return curveQuotes.size();
    }
}
