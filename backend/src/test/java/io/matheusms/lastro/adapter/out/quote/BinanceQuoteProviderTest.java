package io.matheusms.lastro.adapter.out.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.matheusms.lastro.domain.model.Asset;
import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quote;
import io.matheusms.lastro.domain.model.QuoteSource;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BinanceQuoteProviderTest {

    private static final Asset BTC = new Asset(7L, "BTC", "Bitcoin", AssetClass.CRYPTO, null, Currency.USD, true);

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final BinanceQuoteProvider provider = new BinanceQuoteProvider(builder,
            new QuoteSourceProperties(null, null, null, "https://binance.test"));

    @Test
    void shouldReadDailyCandlesInUtc() {
        // 2026-11-18 and 2026-11-19 at 00:00 UTC
        server.expect(requestTo("https://binance.test/api/v3/klines?symbol=BTCUSDT&interval=1d"
                        + "&startTime=1794960000000&endTime=1795132799999&limit=1000"))
                .andRespond(withSuccess("""
                        [[1794960000000,"64947.36","65002.00","62062.97","63068.22","18699.69",1795046399999,
                          "1188855365.14",3341316,"9415.07","598651365.00","0"],
                         [1795046400000,"63068.21","64080.80","62388.72","63818.01","7932.85",1795132799999,
                          "502940307.97",1837726,"3974.47","252045406.74","0"]]
                        """, MediaType.APPLICATION_JSON));

        var quotes = provider.dailyCloses(BTC, LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 19));

        assertThat(quotes).containsExactly(
                new Quote(7L, LocalDate.of(2026, 11, 18), Money.of("63068.22", Currency.USD), QuoteSource.BINANCE),
                new Quote(7L, LocalDate.of(2026, 11, 19), Money.of("63818.01", Currency.USD), QuoteSource.BINANCE));
        server.verify();
    }

    @Test
    void shouldValueStablecoinsAtOneDollarWithoutCallingTheApi() {
        var usdt = new Asset(8L, "USDT", "Tether", AssetClass.CRYPTO, null, Currency.USD, true);

        var quotes = provider.dailyCloses(usdt, LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 18));

        assertThat(quotes).extracting(Quote::closePrice).containsOnly(Money.of("1", Currency.USD));
        assertThat(quotes).hasSize(2);
    }
}
