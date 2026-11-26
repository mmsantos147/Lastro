package io.matheusms.lastro.domain.service;

import io.matheusms.lastro.domain.model.AssetHistory;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Distribution;
import io.matheusms.lastro.domain.model.EntrySource;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quantity;
import io.matheusms.lastro.domain.model.Trade;
import io.matheusms.lastro.domain.model.TradeFee;
import io.matheusms.lastro.domain.model.TradeType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Prepares a user's histories for {@link PositionCalculator}, which works in BRL only (ADR 0002, section 5). Pure
 * function: market data comes in as {@link MarketPrices}.
 * <ul>
 *   <li>Trades priced in USD (or USDT) are converted with the USD/BRL rate of the trade date.</li>
 *   <li>A fee paid in an asset (e.g. BNB) enters the trade cost at that asset's value on the date and is taken
 *       from the fee asset's position, as a sell without proceeds (it leaves at the average cost). When the fee is
 *       paid in the asset being traded, its value is the trade's own unit price.</li>
 *   <li>A distribution paid in an asset (staking reward) adds that quantity to the asset at its value on the
 *       payment date, which becomes its acquisition cost.</li>
 * </ul>
 * It also derives the cash flows that separate contributions from returns: purchases (with fees paid in cash) are
 * contributions; sale proceeds and cash distributions are withdrawals. Fees paid in an asset and staking rewards
 * happen inside the portfolio and are not cash flows.
 * <p>
 * Last, mergers move positions to their successor assets ({@link MergerResolver}), which needs BRL histories.
 */
public class BrlConverter {

    private final MergerResolver mergers = new MergerResolver();

    /**
     * @param histories every history of the user, by asset id, as stored
     * @param distributions every distribution of the user
     * @throws MissingMarketDataException if a rate or price needed for the conversion is not available
     */
    public ConvertedPortfolio convert(
            Map<Long, AssetHistory> histories, List<Distribution> distributions, MarketPrices prices) {
        var tradesByAsset = new LinkedHashMap<Long, List<Trade>>();
        histories.forEach((assetId, history) -> tradesByAsset.put(assetId, new ArrayList<>()));
        var cashFlows = new ArrayList<CashFlow>();

        for (AssetHistory history : histories.values()) {
            for (Trade trade : history.trades()) {
                var converted = toBrl(trade, prices);
                tradesByAsset.get(trade.assetId()).add(converted.trade());
                converted.feeDeduction().ifPresent(deduction ->
                        tradesByAsset.computeIfAbsent(deduction.assetId(), id -> new ArrayList<>()).add(deduction));
                cashFlows.add(converted.cashFlow());
            }
        }
        for (Distribution distribution : distributions) {
            if (distribution.paidInAssetId() != null) {
                tradesByAsset.computeIfAbsent(distribution.paidInAssetId(), id -> new ArrayList<>())
                        .add(rewardAsPurchase(distribution, prices));
            } else {
                var net = inBrl(distribution.netAmount(), distribution.paymentDate(), prices);
                cashFlows.add(new CashFlow(distribution.paymentDate(), negate(net)));
            }
        }

        var converted = new HashMap<Long, AssetHistory>();
        tradesByAsset.forEach((assetId, trades) -> {
            var original = histories.get(assetId);
            converted.put(assetId, original == null
                    ? new AssetHistory(assetId, trades, List.of(), List.of())
                    : new AssetHistory(assetId, trades, original.transfers(), original.corporateActions()));
        });
        return new ConvertedPortfolio(mergers.resolve(converted), List.copyOf(cashFlows));
    }

    private ConvertedTrade toBrl(Trade trade, MarketPrices prices) {
        var unitPrice = inBrl(trade.unitPrice(), trade.date(), prices);
        Money fee;
        Money cashFee;
        Trade deduction = null;
        switch (trade.fee()) {
            case TradeFee.InCurrency(Money amount) -> {
                fee = inBrl(amount, trade.date(), prices);
                cashFee = fee;
            }
            case TradeFee.InAsset(Long feeAssetId, Quantity feeQuantity) -> {
                var feeUnitPrice = feeAssetId.equals(trade.assetId())
                        ? unitPrice
                        : prices.closeInBrlOn(feeAssetId, trade.date()).orElseThrow(() -> new MissingMarketDataException(
                                "No price for fee asset %d on %s".formatted(feeAssetId, trade.date())));
                fee = feeUnitPrice.times(feeQuantity);
                cashFee = Money.zero(Currency.BRL);
                deduction = new Trade(null, trade.userId(), feeAssetId, trade.institutionId(), TradeType.SELL,
                        trade.date(), feeQuantity, Money.zero(Currency.BRL), TradeFee.none(Currency.BRL),
                        trade.source(), null);
            }
        }
        var converted = new Trade(trade.id(), trade.userId(), trade.assetId(), trade.institutionId(), trade.type(),
                trade.date(), trade.quantity(), unitPrice, new TradeFee.InCurrency(fee), trade.source(),
                trade.externalId());
        var gross = unitPrice.times(trade.quantity());
        var cashFlow = trade.type() == TradeType.BUY
                ? new CashFlow(trade.date(), gross.plus(cashFee))
                : new CashFlow(trade.date(), negate(gross.minus(cashFee)));
        return new ConvertedTrade(converted, Optional.ofNullable(deduction), cashFlow);
    }

    private Trade rewardAsPurchase(Distribution reward, MarketPrices prices) {
        var value = inBrl(reward.grossAmount(), reward.paymentDate(), prices);
        var unitPrice = value.dividedBy(reward.paidQuantity());
        return new Trade(null, reward.userId(), reward.paidInAssetId(), reward.institutionId(), TradeType.BUY,
                reward.paymentDate(), reward.paidQuantity(), unitPrice, TradeFee.none(Currency.BRL),
                EntrySource.MANUAL, null);
    }

    private static Money inBrl(Money money, LocalDate date, MarketPrices prices) {
        return prices.inBrl(money, date).orElseThrow(() -> new MissingMarketDataException(
                "No USD/BRL rate on or before " + date));
    }

    private static Money negate(Money money) {
        return Money.zero(money.currency()).minus(money);
    }

    private record ConvertedTrade(Trade trade, Optional<Trade> feeDeduction, CashFlow cashFlow) {
    }

    /**
     * @param histories BRL histories by asset id, including assets touched only by fee deductions or rewards
     * @param cashFlows in BRL, in no particular order
     */
    public record ConvertedPortfolio(Map<Long, AssetHistory> histories, List<CashFlow> cashFlows) {
    }
}
