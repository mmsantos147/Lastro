package io.matheusms.lastro.domain.service;

/** Thrown when a sell trade exceeds the quantity held at that point in time. */
public class InsufficientQuantityException extends RuntimeException {

    public InsufficientQuantityException(String message) {
        super(message);
    }
}
