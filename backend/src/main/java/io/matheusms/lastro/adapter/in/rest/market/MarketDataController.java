package io.matheusms.lastro.adapter.in.rest.market;

import io.matheusms.lastro.application.MarketDataService;
import io.matheusms.lastro.application.MarketDataService.RefreshResult;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.IndicatorType;
import io.matheusms.lastro.domain.model.Quote;
import io.matheusms.lastro.domain.model.QuoteSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
class MarketDataController {

    private final MarketDataService marketData;

    MarketDataController(MarketDataService marketData) {
        this.marketData = marketData;
    }

    /** Fetches missing quotes and indicators now (also done by the scheduled job). */
    @PostMapping("/refresh")
    RefreshResult refresh() {
        return marketData.refresh();
    }

    @GetMapping("/indicators")
    List<IndicatorStatusResponse> indicators() {
        return marketData.indicatorStatus().stream()
                .map(status -> new IndicatorStatusResponse(status.type(), status.latestDate().orElse(null)))
                .toList();
    }

    @GetMapping("/assets/{assetId}/quotes")
    List<QuoteResponse> quotes(@PathVariable Long assetId) {
        return marketData.quotesOf(assetId).stream().map(QuoteResponse::from).toList();
    }

    /** Manual quote, for assets without an automatic source (e.g. Tesouro Direto). */
    @PostMapping("/assets/{assetId}/quotes")
    @ResponseStatus(HttpStatus.CREATED)
    QuoteResponse saveQuote(@PathVariable Long assetId, @Valid @RequestBody QuoteRequest request) {
        return QuoteResponse.from(marketData.saveManualQuote(assetId, request.date(), request.price()));
    }

    @DeleteMapping("/assets/{assetId}/quotes/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteQuote(@PathVariable Long assetId, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        marketData.deleteQuote(assetId, date);
    }

    record IndicatorStatusResponse(IndicatorType type, LocalDate latestDate) {
    }

    record QuoteRequest(@NotNull LocalDate date, @NotNull @PositiveOrZero BigDecimal price) {
    }

    record QuoteResponse(LocalDate date, BigDecimal closePrice, Currency currency, QuoteSource source) {

        static QuoteResponse from(Quote quote) {
            return new QuoteResponse(quote.date(), quote.closePrice().amount(), quote.closePrice().currency(),
                    quote.source());
        }
    }
}
