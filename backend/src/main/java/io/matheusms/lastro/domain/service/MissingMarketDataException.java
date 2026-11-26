package io.matheusms.lastro.domain.service;

/** A calculation needs a price or exchange rate that has not been loaded yet. */
public class MissingMarketDataException extends RuntimeException {

    public MissingMarketDataException(String message) {
        super(message);
    }
}
