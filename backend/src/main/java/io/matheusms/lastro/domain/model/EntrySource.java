package io.matheusms.lastro.domain.model;

/** Where a recorded entry (trade, transfer) came from. */
public enum EntrySource {
    MANUAL,
    /** Imported from the spreadsheet exported by the B3 (Brazilian stock exchange) investor portal. */
    B3_IMPORT,
    BINANCE_API,
    BINANCE_CSV
}
