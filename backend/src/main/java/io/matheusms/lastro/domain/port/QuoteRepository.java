package io.matheusms.lastro.domain.port;

import io.matheusms.lastro.domain.model.Quote;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuoteRepository {

    /** Inserts or replaces the quote of the same asset, date and currency. */
    void upsert(Collection<Quote> quotes);

    /** Quotes of the assets up to {@code to} (inclusive), ordered by asset and date. */
    List<Quote> findByAssetsUpTo(Collection<Long> assetIds, LocalDate to);

    Optional<LocalDate> findLatestDate(Long assetId);

    Optional<LocalDate> findEarliestDate(Long assetId);

    List<Quote> findByAsset(Long assetId);

    void delete(Long assetId, LocalDate date);
}
