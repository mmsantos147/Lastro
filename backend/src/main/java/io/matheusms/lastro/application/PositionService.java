package io.matheusms.lastro.application;

import io.matheusms.lastro.application.PortfolioLoader.UserPortfolio;
import io.matheusms.lastro.domain.model.Asset;
import io.matheusms.lastro.domain.model.AssetHistory;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Position;
import io.matheusms.lastro.domain.model.Quantity;
import io.matheusms.lastro.domain.port.AssetRepository;
import io.matheusms.lastro.domain.service.BrlConverter;
import io.matheusms.lastro.domain.service.MarketPrices;
import io.matheusms.lastro.domain.service.PositionCalculator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Derives positions from the recorded history (trades are the source of truth; nothing is cached) and values them
 * at the latest quote.
 */
@Service
public class PositionService {

    private final AssetRepository assets;
    private final PortfolioLoader loader;
    private final Clock clock;
    private final BrlConverter converter = new BrlConverter();
    private final PositionCalculator calculator = new PositionCalculator();

    PositionService(AssetRepository assets, PortfolioLoader loader, Clock clock) {
        this.assets = assets;
        this.loader = loader;
        this.clock = clock;
    }

    /**
     * Open positions of the user, ordered by asset code.
     *
     * @throws io.matheusms.lastro.domain.service.MissingMarketDataException if a USD/BRL rate or fee asset price
     *     needed to convert a trade has not been loaded yet
     */
    @Transactional(readOnly = true)
    public List<AssetPosition> openPositions(Long userId) {
        var today = LocalDate.now(clock);
        var portfolio = loader.load(userId);
        var prices = loader.marketData(portfolio, today);
        var converted = converter.convert(portfolio.histories(), portfolio.distributions(), prices);
        var result = new ArrayList<AssetPosition>();
        converted.histories().forEach((assetId, history) -> {
            var position = calculator.calculate(history);
            if (!position.isClosed()) {
                var price = prices.closeInBrlOn(assetId, today).orElse(null);
                var priceDate = prices.closeDateOn(assetId, today).orElse(null);
                result.add(new AssetPosition(asset(assetId), position, price, priceDate));
            }
        });
        result.sort(Comparator.comparing(position -> position.asset().code()));
        return result;
    }

    /**
     * Checks that the portfolio is consistent, e.g. after a change that is about to be saved. Only quantities
     * matter here, so no market data is needed.
     *
     * @throws io.matheusms.lastro.domain.service.InsufficientQuantityException if a sell, transfer or fee paid in
     *     an asset exceeds the quantity held at its date
     */
    void requireConsistent(UserPortfolio portfolio) {
        converter.convert(portfolio.histories(), portfolio.distributions(), MarketPrices.QUANTITIES_ONLY)
                .histories().values()
                .forEach(calculator::calculate);
    }

    /** Quantity of the asset held at the end of the date, before any corporate action recorded after it. */
    Quantity quantityOn(UserPortfolio portfolio, Long assetId, LocalDate date) {
        var history = converter.convert(portfolio.histories(), portfolio.distributions(), MarketPrices.QUANTITIES_ONLY)
                .histories().get(assetId);
        if (history == null) {
            return Quantity.ZERO;
        }
        var upToDate = new AssetHistory(assetId,
                history.trades().stream().filter(trade -> !trade.date().isAfter(date)).toList(),
                history.transfers().stream().filter(transfer -> !transfer.date().isAfter(date)).toList(),
                history.corporateActions().stream().filter(action -> !action.recordDate().isAfter(date)).toList());
        return calculator.calculate(upToDate).quantity();
    }

    private Asset asset(Long assetId) {
        return assets.findById(assetId).orElseThrow(() -> new NotFoundException("Asset", assetId));
    }

    /**
     * @param price latest close in BRL; {@code null} when the asset has no quote yet
     * @param priceDate date of that close
     */
    public record AssetPosition(Asset asset, Position position, Money price, LocalDate priceDate) {

        /** Market value in BRL, or the total cost when there is no quote. */
        public Money marketValue() {
            return price == null ? position.totalCost() : price.times(position.quantity());
        }

        public boolean hasQuote() {
            return price != null;
        }
    }
}
