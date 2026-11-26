package io.matheusms.lastro.adapter.in.rest.entry;

import io.matheusms.lastro.adapter.in.rest.CurrentUser;
import io.matheusms.lastro.application.TradeService;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.EntrySource;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quantity;
import io.matheusms.lastro.domain.model.Trade;
import io.matheusms.lastro.domain.model.TradeFee;
import io.matheusms.lastro.domain.model.TradeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trades")
class TradeController {

    private final TradeService trades;

    TradeController(TradeService trades) {
        this.trades = trades;
    }

    @GetMapping
    List<TradeResponse> list(@CurrentUser Long userId) {
        return trades.list(userId).stream().map(TradeResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TradeResponse create(@CurrentUser Long userId, @Valid @RequestBody TradeRequest request) {
        return TradeResponse.from(trades.register(request.toDomain(userId)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@CurrentUser Long userId, @PathVariable Long id) {
        trades.delete(userId, id);
    }

    /**
     * The fee is either {@code feeAmount} (in the price currency; zero when omitted) or {@code feeAssetId} +
     * {@code feeQuantity} (paid in units of another asset, e.g. BNB).
     */
    record TradeRequest(
            @NotNull Long assetId,
            @NotNull Long institutionId,
            @NotNull TradeType type,
            @NotNull LocalDate date,
            @NotNull BigDecimal quantity,
            @NotNull BigDecimal unitPrice,
            @NotNull Currency currency,
            BigDecimal feeAmount,
            Long feeAssetId,
            BigDecimal feeQuantity) {

        Trade toDomain(Long userId) {
            return new Trade(null, userId, assetId, institutionId, type, date, new Quantity(quantity),
                    new Money(unitPrice, currency), fee(), EntrySource.MANUAL, null);
        }

        private TradeFee fee() {
            if (feeAssetId != null || feeQuantity != null) {
                if (feeAssetId == null || feeQuantity == null || feeAmount != null) {
                    throw new IllegalArgumentException(
                            "A fee paid in an asset requires feeAssetId and feeQuantity, without feeAmount");
                }
                return new TradeFee.InAsset(feeAssetId, new Quantity(feeQuantity));
            }
            return new TradeFee.InCurrency(new Money(feeAmount == null ? BigDecimal.ZERO : feeAmount, currency));
        }
    }

    record TradeResponse(
            Long id,
            Long assetId,
            Long institutionId,
            TradeType type,
            LocalDate date,
            BigDecimal quantity,
            BigDecimal unitPrice,
            Currency currency,
            BigDecimal feeAmount,
            Long feeAssetId,
            BigDecimal feeQuantity,
            EntrySource source,
            String externalId) {

        static TradeResponse from(Trade trade) {
            BigDecimal feeAmount = null;
            Long feeAssetId = null;
            BigDecimal feeQuantity = null;
            switch (trade.fee()) {
                case TradeFee.InCurrency(Money amount) -> feeAmount = amount.amount();
                case TradeFee.InAsset(Long assetId, Quantity quantity) -> {
                    feeAssetId = assetId;
                    feeQuantity = quantity.value();
                }
            }
            return new TradeResponse(trade.id(), trade.assetId(), trade.institutionId(), trade.type(), trade.date(),
                    trade.quantity().value(), trade.unitPrice().amount(), trade.priceCurrency(), feeAmount,
                    feeAssetId, feeQuantity, trade.source(), trade.externalId());
        }
    }
}
