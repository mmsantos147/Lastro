package io.matheusms.lastro.application;

/** A referenced resource does not exist or does not belong to the current user. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, Long id) {
        super("%s %d not found".formatted(resource, id));
    }
}
