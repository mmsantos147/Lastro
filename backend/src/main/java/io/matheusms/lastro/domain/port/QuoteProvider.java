package io.matheusms.lastro.domain.port;

import io.matheusms.lastro.domain.model.Asset;
import io.matheusms.lastro.domain.model.Quote;
import java.time.LocalDate;
import java.util.List;

/** External source of daily closing prices (brapi for B3 assets, Binance for crypto). */
public interface QuoteProvider {

    boolean supports(Asset asset);

    /**
     * Daily closes between {@code from} and {@code to} (inclusive), in the asset's quote currency. May return
     * fewer days than asked when the source limits how far back it goes.
     */
    List<Quote> dailyCloses(Asset asset, LocalDate from, LocalDate to);
}
