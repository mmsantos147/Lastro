package io.matheusms.lastro.adapter.out.indicator;

import io.matheusms.lastro.domain.model.EconomicIndicator;
import io.matheusms.lastro.domain.model.IndicatorType;
import io.matheusms.lastro.domain.port.EconomicIndicatorProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Banco Central SGS series ({@code GET /dados/serie/bcdata.sgs.{code}/dados?formato=json&dataInicial=&dataFinal=}).
 * Daily series accept at most a 10-year window per request, so longer periods are split.
 */
@Component
class SgsIndicatorProvider implements EconomicIndicatorProvider {

    /** Series codes, confirmed on 2026-11-19: CDI and Selic in % per day, IPCA in % per month, PTAX selling rate. */
    static final Map<IndicatorType, Integer> SERIES = Map.of(
            IndicatorType.CDI, 12,
            IndicatorType.SELIC, 11,
            IndicatorType.IPCA, 433,
            IndicatorType.USD_BRL, 1);

    private static final DateTimeFormatter SGS_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int MAX_WINDOW_YEARS = 10;

    private final RestClient client;

    SgsIndicatorProvider(RestClient.Builder builder, @Value("${lastro.market.sgs-base-url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public boolean supports(IndicatorType type) {
        return SERIES.containsKey(type);
    }

    @Override
    public List<EconomicIndicator> fetch(IndicatorType type, LocalDate from, LocalDate to) {
        var values = new ArrayList<EconomicIndicator>();
        for (var start = from; !start.isAfter(to); start = start.plusYears(MAX_WINDOW_YEARS)) {
            var end = start.plusYears(MAX_WINDOW_YEARS).minusDays(1);
            values.addAll(fetchWindow(type, start, end.isAfter(to) ? to : end));
        }
        return values;
    }

    private List<EconomicIndicator> fetchWindow(IndicatorType type, LocalDate from, LocalDate to) {
        JsonNode body;
        try {
            body = client.get()
                    .uri("/dados/serie/bcdata.sgs.{code}/dados?formato=json&dataInicial={from}&dataFinal={to}",
                            SERIES.get(type), SGS_DATE.format(from), SGS_DATE.format(to))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException exception) {
            // SGS answers 404 when the window has no values yet (e.g. today before publication)
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return List.of();
            }
            throw exception;
        }
        var values = new ArrayList<EconomicIndicator>();
        for (JsonNode entry : body) {
            values.add(new EconomicIndicator(type, LocalDate.parse(entry.path("data").asString(), SGS_DATE),
                    new BigDecimal(entry.path("valor").asString())));
        }
        return values;
    }
}
