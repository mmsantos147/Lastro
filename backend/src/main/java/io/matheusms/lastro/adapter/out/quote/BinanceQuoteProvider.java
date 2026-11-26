package io.matheusms.lastro.adapter.out.quote;

import io.matheusms.lastro.domain.model.Asset;
import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quote;
import io.matheusms.lastro.domain.model.QuoteSource;
import io.matheusms.lastro.domain.port.QuoteProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Crypto daily closes from the Binance public market data API ({@code GET /api/v3/klines}, no key needed), using
 * the {@code <CODE>USDT} pair. Candles are in UTC; prices in USDT are recorded as USD. Stablecoins are worth 1 USD.
 */
@Component
@EnableConfigurationProperties(QuoteSourceProperties.class)
class BinanceQuoteProvider implements QuoteProvider {

    private static final Set<String> STABLECOINS = Set.of("USDT", "USDC", "FDUSD", "DAI");
    private static final int MAX_CANDLES = 1000;
    private static final int OPEN_TIME = 0;
    private static final int CLOSE_PRICE = 4;

    private final RestClient client;

    BinanceQuoteProvider(RestClient.Builder builder, QuoteSourceProperties properties) {
        this.client = builder.baseUrl(properties.binanceBaseUrl()).build();
    }

    @Override
    public boolean supports(Asset asset) {
        return asset.assetClass() == AssetClass.CRYPTO && asset.quoteCurrency() == Currency.USD;
    }

    @Override
    public List<Quote> dailyCloses(Asset asset, LocalDate from, LocalDate to) {
        if (STABLECOINS.contains(asset.code())) {
            return from.datesUntil(to.plusDays(1))
                    .map(date -> quote(asset, date, BigDecimal.ONE))
                    .toList();
        }
        var quotes = new ArrayList<Quote>();
        var start = from;
        while (!start.isAfter(to)) {
            var candles = klines(asset.code() + "USDT", start, to);
            for (JsonNode candle : candles) {
                var date = Instant.ofEpochMilli(candle.get(OPEN_TIME).asLong()).atZone(ZoneOffset.UTC).toLocalDate();
                quotes.add(quote(asset, date, new BigDecimal(candle.get(CLOSE_PRICE).asString())));
            }
            if (candles.size() < MAX_CANDLES) {
                break;
            }
            start = quotes.getLast().date().plusDays(1);
        }
        return quotes;
    }

    private JsonNode klines(String symbol, LocalDate from, LocalDate to) {
        long startTime = from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long endTime = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;
        return client.get()
                .uri("/api/v3/klines?symbol={symbol}&interval=1d&startTime={start}&endTime={end}&limit={limit}",
                        symbol, startTime, endTime, MAX_CANDLES)
                .retrieve()
                .body(JsonNode.class);
    }

    private static Quote quote(Asset asset, LocalDate date, BigDecimal close) {
        return new Quote(asset.id(), date, new Money(close, Currency.USD), QuoteSource.BINANCE);
    }
}
