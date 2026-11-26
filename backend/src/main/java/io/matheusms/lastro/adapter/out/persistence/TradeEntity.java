package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.EntrySource;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quantity;
import io.matheusms.lastro.domain.model.Trade;
import io.matheusms.lastro.domain.model.TradeFee;
import io.matheusms.lastro.domain.model.TradeType;
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
@Table(name = "trade")
class TradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "institution_id", nullable = false)
    private Long institutionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private TradeType type;

    @Column(name = "trade_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_currency", nullable = false, length = 3)
    private Currency priceCurrency;

    // Fee: either amount + currency or asset + quantity (see TradeFee and the ck_trade_fee constraint)
    @Column(name = "fee_amount", precision = 20, scale = 8)
    private BigDecimal feeAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_currency", length = 3)
    private Currency feeCurrency;

    @Column(name = "fee_asset_id")
    private Long feeAssetId;

    @Column(name = "fee_quantity", precision = 20, scale = 8)
    private BigDecimal feeQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EntrySource source;

    @Column(name = "external_id", length = 100)
    private String externalId;

    protected TradeEntity() {
    }

    static TradeEntity from(Trade trade) {
        var entity = new TradeEntity();
        entity.id = trade.id();
        entity.userId = trade.userId();
        entity.assetId = trade.assetId();
        entity.institutionId = trade.institutionId();
        entity.type = trade.type();
        entity.date = trade.date();
        entity.quantity = trade.quantity().value();
        entity.unitPrice = trade.unitPrice().amount();
        entity.priceCurrency = trade.priceCurrency();
        switch (trade.fee()) {
            case TradeFee.InCurrency(Money amount) -> {
                entity.feeAmount = amount.amount();
                entity.feeCurrency = amount.currency();
            }
            case TradeFee.InAsset(Long feeAssetId, Quantity feeQuantity) -> {
                entity.feeAssetId = feeAssetId;
                entity.feeQuantity = feeQuantity.value();
            }
        }
        entity.source = trade.source();
        entity.externalId = trade.externalId();
        return entity;
    }

    Trade toDomain() {
        TradeFee fee = feeAssetId == null
                ? new TradeFee.InCurrency(new Money(feeAmount, feeCurrency))
                : new TradeFee.InAsset(feeAssetId, new Quantity(feeQuantity));
        return new Trade(id, userId, assetId, institutionId, type, date,
                new Quantity(quantity), new Money(unitPrice, priceCurrency), fee, source, externalId);
    }
}
