package io.matheusms.lastro.adapter.out.quote;

import io.matheusms.lastro.domain.model.EconomicIndicator;
import io.matheusms.lastro.domain.model.IndicatorType;
import io.matheusms.lastro.domain.port.EconomicIndicatorProvider;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Ibovespa closing level ({@code ^BVSP}), which brapi serves only with a token. */
@Component
class BrapiIbovespaProvider implements EconomicIndicatorProvider {

    private static final Logger log = LoggerFactory.getLogger(BrapiIbovespaProvider.class);
    private static final String IBOVESPA = "^BVSP";

    private final BrapiClient brapi;

    BrapiIbovespaProvider(BrapiClient brapi) {
        this.brapi = brapi;
    }

    @Override
    public boolean supports(IndicatorType type) {
        return type == IndicatorType.IBOV;
    }

    @Override
    public List<EconomicIndicator> fetch(IndicatorType type, LocalDate from, LocalDate to) {
        if (!brapi.hasToken()) {
            log.warn("BRAPI_TOKEN is not set: Ibovespa comparison is unavailable");
            return List.of();
        }
        return brapi.dailyCloses(IBOVESPA, from).stream()
                .filter(close -> !close.date().isBefore(from) && !close.date().isAfter(to))
                .map(close -> new EconomicIndicator(IndicatorType.IBOV, close.date(), close.close()))
                .toList();
    }
}
