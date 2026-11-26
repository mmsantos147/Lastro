package io.matheusms.lastro.application;

import io.matheusms.lastro.domain.model.AssetHistory;
import io.matheusms.lastro.domain.model.Trade;
import io.matheusms.lastro.domain.model.TradeFee;
import io.matheusms.lastro.domain.port.TradeRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Buys and sells recorded by the user. */
@Service
public class TradeService {

    private final TradeRepository trades;
    private final EntryValidator validator;

    TradeService(TradeRepository trades, EntryValidator validator) {
        this.trades = trades;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public List<Trade> list(Long userId) {
        return trades.findByUser(userId);
    }

    /** @throws io.matheusms.lastro.domain.service.InsufficientQuantityException if a sell exceeds the position */
    @Transactional
    public Trade register(Trade trade) {
        validator.requireAsset(trade.assetId());
        validator.requireInstitution(trade.institutionId());
        if (trade.fee() instanceof TradeFee.InAsset(Long feeAssetId, _)) {
            validator.requireAsset(feeAssetId);
        }
        validator.requireConsistentPortfolio(trade.userId(),
                portfolio -> portfolio.withHistory(trade.assetId(), history -> withTrade(history, trade)));
        return trades.save(trade);
    }

    @Transactional
    public void delete(Long userId, Long tradeId) {
        var trade = trades.findById(tradeId)
                .filter(found -> found.userId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Trade", tradeId));
        validator.requireConsistentPortfolio(userId,
                portfolio -> portfolio.withHistory(trade.assetId(), history -> withoutTrade(history, tradeId)));
        trades.deleteById(tradeId);
    }

    private static AssetHistory withTrade(AssetHistory history, Trade trade) {
        var updated = new ArrayList<>(history.trades());
        updated.add(trade);
        return new AssetHistory(history.assetId(), updated, history.transfers(), history.corporateActions());
    }

    private static AssetHistory withoutTrade(AssetHistory history, Long tradeId) {
        var updated = history.trades().stream().filter(trade -> !tradeId.equals(trade.id())).toList();
        return new AssetHistory(history.assetId(), updated, history.transfers(), history.corporateActions());
    }
}
