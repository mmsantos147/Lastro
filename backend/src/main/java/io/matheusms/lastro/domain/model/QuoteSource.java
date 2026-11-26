package io.matheusms.lastro.domain.model;

/** Where a quote came from. */
public enum QuoteSource {
    /** brapi.dev: B3-listed stocks, FIIs and ETFs. */
    BRAPI,
    /** Binance public market data: crypto. */
    BINANCE,
    /** Typed by the user, for assets without an automatic source (e.g. Tesouro Direto). */
    MANUAL,
    /** Computed from the terms of a private fixed income security and the CDI/Selic/IPCA series. */
    CURVE
}
