package io.matheusms.lastro.adapter.in.rest.portfolio;

import io.matheusms.lastro.adapter.in.rest.CurrentUser;
import io.matheusms.lastro.adapter.in.rest.Ratios;
import io.matheusms.lastro.application.PositionService;
import io.matheusms.lastro.application.PositionService.AssetPosition;
import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Money;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/positions")
class PositionController {

    private final PositionService positions;

    PositionController(PositionService positions) {
        this.positions = positions;
    }

    @GetMapping
    List<PositionResponse> list(@CurrentUser Long userId, @RequestParam(required = false) AssetClass assetClass) {
        var all = positions.openPositions(userId);
        var total = all.stream().map(AssetPosition::marketValue).reduce(Money.zero(Currency.BRL), Money::plus);
        return all.stream()
                .filter(position -> assetClass == null || position.asset().assetClass() == assetClass)
                .map(position -> PositionResponse.from(position, total))
                .toList();
    }

    /**
     * Amounts in BRL. Without a quote, {@code price}, {@code priceDate} and the result fields are {@code null} and
     * the market value falls back to the total cost.
     *
     * @param weight share of the position in the whole portfolio (0.1 = 10%)
     */
    record PositionResponse(
            Long assetId,
            String code,
            String name,
            AssetClass assetClass,
            BigDecimal quantity,
            BigDecimal averageCost,
            BigDecimal totalCost,
            BigDecimal price,
            LocalDate priceDate,
            BigDecimal marketValue,
            BigDecimal result,
            BigDecimal resultPercent,
            BigDecimal weight) {

        static PositionResponse from(AssetPosition assetPosition, Money portfolioValue) {
            var asset = assetPosition.asset();
            var position = assetPosition.position();
            var totalCost = position.totalCost();
            var marketValue = assetPosition.marketValue();
            BigDecimal result = null;
            BigDecimal resultPercent = null;
            if (assetPosition.hasQuote()) {
                result = marketValue.minus(totalCost).roundedToCents().amount();
                resultPercent = totalCost.isZero() ? null
                        : marketValue.amount().divide(totalCost.amount(), MathContext.DECIMAL64).subtract(BigDecimal.ONE);
            }
            var weight = portfolioValue.isZero() ? BigDecimal.ZERO
                    : marketValue.amount().divide(portfolioValue.amount(), MathContext.DECIMAL64);
            return new PositionResponse(asset.id(), asset.code(), asset.name(), asset.assetClass(),
                    position.quantity().value(), position.averageCost().amount(), totalCost.roundedToCents().amount(),
                    assetPosition.hasQuote() ? assetPosition.price().amount() : null, assetPosition.priceDate(),
                    marketValue.roundedToCents().amount(), result, Ratios.rounded(resultPercent), Ratios.rounded(weight));
        }
    }
}
