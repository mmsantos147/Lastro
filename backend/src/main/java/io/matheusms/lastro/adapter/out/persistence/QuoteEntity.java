package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quote;
import io.matheusms.lastro.domain.model.QuoteSource;
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
@Table(name = "quote")
class QuoteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "quote_date", nullable = false)
    private LocalDate date;

    @Column(name = "close_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal closePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private QuoteSource source;

    protected QuoteEntity() {
    }

    Quote toDomain() {
        return new Quote(assetId, date, new Money(closePrice, currency), source);
    }
}
