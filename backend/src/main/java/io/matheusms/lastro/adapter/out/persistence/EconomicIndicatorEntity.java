package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.EconomicIndicator;
import io.matheusms.lastro.domain.model.IndicatorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "economic_indicator")
class EconomicIndicatorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private IndicatorType type;

    @Column(name = "indicator_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal value;

    protected EconomicIndicatorEntity() {
    }

    EconomicIndicator toDomain() {
        return new EconomicIndicator(type, date, value);
    }
}
