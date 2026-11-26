package io.matheusms.lastro.adapter.out.indicator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.matheusms.lastro.domain.model.EconomicIndicator;
import io.matheusms.lastro.domain.model.IndicatorType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SgsIndicatorProviderTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final SgsIndicatorProvider provider = new SgsIndicatorProvider(builder, "https://sgs.test");

    @Test
    void shouldReadSeriesValuesWithBrazilianDates() {
        server.expect(requestTo("https://sgs.test/dados/serie/bcdata.sgs.12/dados?formato=json"
                        + "&dataInicial=03%2F11%2F2026&dataFinal=04%2F11%2F2026"))
                .andRespond(withSuccess("""
                        [{"data":"03/11/2026","valor":"0.050788"},{"data":"04/11/2026","valor":"0.050788"}]
                        """, MediaType.APPLICATION_JSON));

        var values = provider.fetch(IndicatorType.CDI, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 4));

        assertThat(values).containsExactly(
                new EconomicIndicator(IndicatorType.CDI, LocalDate.of(2026, 11, 3), new BigDecimal("0.050788")),
                new EconomicIndicator(IndicatorType.CDI, LocalDate.of(2026, 11, 4), new BigDecimal("0.050788")));
    }

    @Test
    void shouldSplitPeriodsLongerThanTenYears() {
        server.expect(once(), requestTo("https://sgs.test/dados/serie/bcdata.sgs.1/dados?formato=json"
                        + "&dataInicial=01%2F01%2F2010&dataFinal=31%2F12%2F2019"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://sgs.test/dados/serie/bcdata.sgs.1/dados?formato=json"
                        + "&dataInicial=01%2F01%2F2020&dataFinal=05%2F01%2F2020"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        provider.fetch(IndicatorType.USD_BRL, LocalDate.of(2010, 1, 1), LocalDate.of(2020, 1, 5));

        server.verify();
    }

    @Test
    void shouldTreatNotFoundAsNoValuesYet() {
        server.expect(requestTo("https://sgs.test/dados/serie/bcdata.sgs.433/dados?formato=json"
                        + "&dataInicial=19%2F11%2F2026&dataFinal=19%2F11%2F2026"))
                .andRespond(withResourceNotFound());

        assertThat(provider.fetch(IndicatorType.IPCA, LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 19)))
                .isEmpty();
    }

    @Test
    void shouldNotSupportIbovespa() {
        assertThat(provider.supports(IndicatorType.IBOV)).isFalse();
    }
}
