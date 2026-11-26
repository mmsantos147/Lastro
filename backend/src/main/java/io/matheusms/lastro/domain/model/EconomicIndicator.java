package io.matheusms.lastro.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Value of an indicator on a date; see {@link IndicatorType} for the unit of each type. */
public record EconomicIndicator(IndicatorType type, LocalDate date, BigDecimal value) {

    public EconomicIndicator {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(value, "value must not be null");
    }
}
