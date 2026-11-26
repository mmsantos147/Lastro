package io.matheusms.lastro;

import static io.matheusms.lastro.ApiTestSupport.unique;
import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.EconomicIndicator;
import io.matheusms.lastro.domain.model.IndicatorType;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quote;
import io.matheusms.lastro.domain.model.QuoteSource;
import io.matheusms.lastro.domain.port.EconomicIndicatorRepository;
import io.matheusms.lastro.domain.port.QuoteRepository;
import io.matheusms.lastro.domain.port.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Positions valued at the latest quote and returns, with market data stored as the scheduled job would. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PortfolioValuationIT {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private QuoteRepository quotes;
    @Autowired
    private EconomicIndicatorRepository indicators;

    private final JsonMapper json = JsonMapper.builder().build();
    private ApiTestSupport api;
    private long exchangeId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestSupport(users, userDetailsService);
        exchangeId = create("/api/institutions", "{\"name\": \"Exchange\", \"type\": \"EXCHANGE\"}");
    }

    @Test
    void shouldValueCryptoInReaisAtTheLatestQuote() throws Exception {
        var day = LocalDate.of(2025, 3, 3);
        var assetId = create("/api/assets", """
                {"code": "%s", "name": "Coin", "assetClass": "CRYPTO", "quoteCurrency": "USD"}""".formatted(unique("C")));
        indicators.upsert(List.of(new EconomicIndicator(IndicatorType.USD_BRL, day, new BigDecimal("5.00"))));
        quotes.upsert(List.of(new Quote(assetId, day.plusDays(1), Money.of("120", Currency.USD), QuoteSource.BINANCE)));
        create("/api/trades", """
                {"assetId": %d, "institutionId": %d, "type": "BUY", "date": "%s", "quantity": 2,
                 "unitPrice": 100, "currency": "USD", "feeAmount": 0}""".formatted(assetId, exchangeId, day));

        mvc.perform(api.getJson("/api/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].averageCost").value(closeTo(500.0, 0.001)))
                .andExpect(jsonPath("$[0].price").value(closeTo(600.0, 0.001)))
                .andExpect(jsonPath("$[0].marketValue").value(closeTo(1200.0, 0.001)))
                .andExpect(jsonPath("$[0].result").value(closeTo(200.0, 0.001)))
                .andExpect(jsonPath("$[0].weight").value(closeTo(1.0, 0.0001)));

        mvc.perform(api.getJson("/api/portfolio/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value(day.toString()))
                .andExpect(jsonPath("$.marketValue").value(closeTo(1200.0, 0.001)))
                .andExpect(jsonPath("$.invested").value(closeTo(1000.0, 0.001)))
                .andExpect(jsonPath("$.returnSinceStart").value(closeTo(0.2, 0.0001)));
    }

    @Test
    void shouldAskForMarketDataWhenTheExchangeRateIsMissing() throws Exception {
        var assetId = create("/api/assets", """
                {"code": "%s", "name": "Coin", "assetClass": "CRYPTO", "quoteCurrency": "USD"}""".formatted(unique("M")));
        create("/api/trades", """
                {"assetId": %d, "institutionId": %d, "type": "BUY", "date": "1990-01-02", "quantity": 1,
                 "unitPrice": 1, "currency": "USD", "feeAmount": 0}""".formatted(assetId, exchangeId));

        mvc.perform(api.getJson("/api/positions"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MISSING_MARKET_DATA"));
    }

    @Test
    void shouldReportIncomeAndYieldOnCost() throws Exception {
        var paid = LocalDate.now().minusMonths(1);
        var assetId = create("/api/assets", """
                {"code": "%s", "name": "Fund", "assetClass": "FII", "quoteCurrency": "BRL"}""".formatted(unique("F")));
        create("/api/trades", """
                {"assetId": %d, "institutionId": %d, "type": "BUY", "date": "%s", "quantity": 10,
                 "unitPrice": 100, "currency": "BRL", "feeAmount": 0}"""
                .formatted(assetId, exchangeId, paid.minusMonths(2)));
        create("/api/distributions", """
                {"assetId": %d, "institutionId": %d, "type": "FII_INCOME", "recordDate": "%s",
                 "paymentDate": "%s", "baseQuantity": 10, "currency": "BRL", "grossAmount": 8.00}"""
                .formatted(assetId, exchangeId, paid.minusDays(10), paid));

        mvc.perform(api.getJson("/api/portfolio/income?months=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months.length()").value(3))
                .andExpect(jsonPath("$.lastTwelveMonths").value(closeTo(8.0, 0.001)))
                .andExpect(jsonPath("$.yieldOnCost").value(closeTo(0.008, 0.000001)))
                .andExpect(jsonPath("$.byAsset[0].assetId").value(assetId));
    }

    @Test
    void shouldReturnAnEmptySummaryWithoutTrades() throws Exception {
        mvc.perform(api.getJson("/api/portfolio/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketValue").value(0))
                .andExpect(jsonPath("$.startDate").doesNotExist());
        mvc.perform(api.getJson("/api/portfolio/history")).andExpect(jsonPath("$").isEmpty());
    }

    private long create(String path, String body) throws Exception {
        var response = mvc.perform(api.postJson(path, body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }
}
