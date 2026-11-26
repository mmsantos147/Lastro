package io.matheusms.lastro.domain.port;

import io.matheusms.lastro.domain.model.EconomicIndicator;
import io.matheusms.lastro.domain.model.IndicatorType;
import java.time.LocalDate;
import java.util.List;

/** External source of economic indicators (Banco Central SGS; Ibovespa from the quote source). */
public interface EconomicIndicatorProvider {

    boolean supports(IndicatorType type);

    /** Values between {@code from} and {@code to} (inclusive). */
    List<EconomicIndicator> fetch(IndicatorType type, LocalDate from, LocalDate to);
}
