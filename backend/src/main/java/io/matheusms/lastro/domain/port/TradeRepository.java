package io.matheusms.lastro.domain.port;

import io.matheusms.lastro.domain.model.Trade;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TradeRepository {

    Trade save(Trade trade);

    /** Trades of a user in an asset, ordered by date and then by insertion order. */
    List<Trade> findByUserAndAsset(Long userId, Long assetId);

    /** All trades of a user, ordered by date and then by insertion order. */
    List<Trade> findByUser(Long userId);

    Optional<Trade> findById(Long id);

    /** Assets the user has ever traded. */
    Set<Long> findTradedAssetIds(Long userId);

    void deleteById(Long id);

    /** Date of the first trade of the asset, by any user. */
    Optional<LocalDate> findFirstTradeDate(Long assetId);

    /** First trade of the asset (by date, then insertion order), by any user. */
    Optional<Trade> findFirstTrade(Long assetId);

    /** Date of the first trade recorded, by any user. */
    Optional<LocalDate> findFirstTradeDate();
}
