package io.matheusms.lastro.domain.model;

import java.util.Objects;

final class Preconditions {

    private Preconditions() {
    }

    static String requireNotBlank(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
