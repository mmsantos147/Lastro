package io.matheusms.lastro.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Everything that changes the position of one asset: trades, transfers and corporate actions, each list in
 * any order. Input of the position calculation.
 */
public record AssetHistory(
        Long assetId, List<Trade> trades, List<Transfer> transfers, List<CorporateAction> corporateActions) {

    public AssetHistory {
        Objects.requireNonNull(assetId, "assetId must not be null");
        trades = List.copyOf(trades);
        transfers = List.copyOf(transfers);
        corporateActions = List.copyOf(corporateActions);
    }
}
