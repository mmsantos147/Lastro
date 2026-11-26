package io.matheusms.lastro.adapter.out.quote;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * brapi.dev quote endpoint ({@code GET /api/quote/{ticker}?range=&interval=1d}). One ticker per request, which is
 * what the free plan allows. Dates come as Unix seconds and are read in the B3 time zone.
 */
@Component
@EnableConfigurationProperties(QuoteSourceProperties.class)
class BrapiClient {

    private static final ZoneId B3_ZONE = ZoneId.of("America/Sao_Paulo");
    /** Ranges accepted by brapi, from the shortest; the request uses the shortest one covering the period. */
    private static final List<Map.Entry<String, Long>> RANGES = List.of(
            Map.entry("5d", 5L), Map.entry("1mo", 31L), Map.entry("3mo", 92L), Map.entry("6mo", 183L),
            Map.entry("1y", 366L), Map.entry("2y", 731L), Map.entry("5y", 1827L), Map.entry("10y", 3653L),
            Map.entry("max", Long.MAX_VALUE));

    private final RestClient client;
    private final QuoteSourceProperties properties;
    private final Clock clock;

    BrapiClient(RestClient.Builder builder, QuoteSourceProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.client = builder.baseUrl(properties.brapiBaseUrl())
                .defaultHeaders(headers -> {
                    if (properties.hasBrapiToken()) {
                        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + properties.brapiToken());
                    }
                })
                .build();
    }

    boolean hasToken() {
        return properties.hasBrapiToken();
    }

    /** Daily closes of the ticker from {@code from} until today (or as far back as the plan allows). */
    List<DailyClose> dailyCloses(String ticker, LocalDate from) {
        var response = client.get()
                .uri("/api/quote/{ticker}?range={range}&interval=1d", ticker, range(from))
                .retrieve()
                .body(JsonNode.class);
        var result = response.path("results").path(0);
        var closes = new ArrayList<DailyClose>();
        for (JsonNode price : result.path("historicalDataPrice")) {
            if (price.path("close").isNumber()) {
                closes.add(new DailyClose(toB3Date(price.path("date").asLong()), price.path("close").decimalValue()));
            }
        }
        // The latest price (today's close, or the current price during trading hours)
        if (result.path("regularMarketPrice").isNumber() && result.hasNonNull("regularMarketTime")) {
            var date = Instant.parse(result.path("regularMarketTime").asString()).atZone(B3_ZONE).toLocalDate();
            closes.removeIf(close -> close.date().equals(date));
            closes.add(new DailyClose(date, result.path("regularMarketPrice").decimalValue()));
        }
        return closes;
    }

    private String range(LocalDate from) {
        long days = ChronoUnit.DAYS.between(from, LocalDate.now(clock.withZone(B3_ZONE))) + 1;
        long maxDays = RANGES.stream().filter(range -> range.getKey().equals(properties.brapiMaxRange()))
                .findFirst().map(Map.Entry::getValue).orElse(Long.MAX_VALUE);
        return RANGES.stream()
                .filter(range -> range.getValue() >= Math.min(days, maxDays))
                .findFirst().orElseThrow().getKey();
    }

    private static LocalDate toB3Date(long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds).atZone(B3_ZONE).toLocalDate();
    }

    record DailyClose(LocalDate date, BigDecimal close) {
    }
}
