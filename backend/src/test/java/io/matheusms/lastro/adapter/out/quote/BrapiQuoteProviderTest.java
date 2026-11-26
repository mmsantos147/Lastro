package io.matheusms.lastro.adapter.out.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.matheusms.lastro.domain.model.Asset;
import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.EconomicIndicator;
import io.matheusms.lastro.domain.model.IndicatorType;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quote;
import io.matheusms.lastro.domain.model.QuoteSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BrapiQuoteProviderTest {

    /** The range sent to brapi depends on today: fixed, so the tests do not break as time passes. */
    private static final Clock TODAY = Clock.fixed(Instant.parse("2026-11-18T22:00:00Z"), ZoneOffset.UTC);
    private static final Asset PETR4 = new Asset(5L, "PETR4", "Petrobras", AssetClass.STOCK, null, Currency.BRL, true);
    // 2026-11-16 and 2026-11-17 at 00:00 in São Paulo, as brapi sends them
    private static final String RESPONSE = """
            {"results": [{"symbol": "PETR4", "regularMarketPrice": 43.17,
              "regularMarketTime": "2026-11-18T21:30:30.000Z",
              "historicalDataPrice": [
                {"date": 1794798000, "close": 44.02},
                {"date": 1794884400, "close": 43.98},
                {"date": 1794970800, "close": 43.10}]}]}
            """;

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void shouldReadDailyClosesInTheB3TimeZoneAndTheLatestPrice() {
        var provider = new BrapiQuoteProvider(client(""));
        server.expect(requestTo("https://brapi.test/api/quote/PETR4?range=5d&interval=1d"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        var quotes = provider.dailyCloses(PETR4, LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 18));

        assertThat(quotes).containsExactly(
                quote(LocalDate.of(2026, 11, 17), "43.98"),
                quote(LocalDate.of(2026, 11, 18), "43.17"));
        server.verify();
    }

    @Test
    void shouldSendTheTokenAndCapTheRangeToThePlan() {
        var provider = new BrapiQuoteProvider(client("secret-token"));
        server.expect(requestTo("https://brapi.test/api/quote/PETR4?range=3mo&interval=1d"))
                .andExpect(header("Authorization", "Bearer secret-token"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        provider.dailyCloses(PETR4, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 11, 18));

        server.verify();
    }

    @Test
    void shouldFetchIbovespaOnlyWithAToken() {
        assertThat(new BrapiIbovespaProvider(client("")).fetch(IndicatorType.IBOV,
                LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 18))).isEmpty();

        server.expect(requestTo("https://brapi.test/api/quote/%5EBVSP?range=3mo&interval=1d"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));
        var levels = new BrapiIbovespaProvider(client("token"))
                .fetch(IndicatorType.IBOV, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 17));

        assertThat(levels).containsExactly(
                new EconomicIndicator(IndicatorType.IBOV, LocalDate.of(2026, 11, 16), new BigDecimal("44.02")),
                new EconomicIndicator(IndicatorType.IBOV, LocalDate.of(2026, 11, 17), new BigDecimal("43.98")));
    }

    @Test
    void shouldSupportOnlyB3ListedAssetsInReais() {
        var provider = new BrapiQuoteProvider(client(""));

        assertThat(provider.supports(PETR4)).isTrue();
        assertThat(provider.supports(new Asset(1L, "TESOURO-SELIC-2031", "Tesouro Selic", AssetClass.TREASURY, null,
                Currency.BRL, true))).isFalse();
        assertThat(provider.supports(new Asset(1L, "BTC", "Bitcoin", AssetClass.CRYPTO, null, Currency.USD, true)))
                .isFalse();
    }

    private BrapiClient client(String token) {
        return new BrapiClient(builder, new QuoteSourceProperties("https://brapi.test", token, "3mo", null), TODAY);
    }

    private static Quote quote(LocalDate date, String close) {
        return new Quote(PETR4.id(), date, Money.brl(close), QuoteSource.BRAPI);
    }
}
