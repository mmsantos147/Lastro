package io.matheusms.lastro.application;

/** The request conflicts with existing data (e.g. a duplicated asset code). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
