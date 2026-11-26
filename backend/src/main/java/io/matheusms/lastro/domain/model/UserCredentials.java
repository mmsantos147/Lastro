package io.matheusms.lastro.domain.model;

import static io.matheusms.lastro.domain.model.Preconditions.requireNotBlank;

import java.util.Objects;

/**
 * A user together with the stored password hash, used only for authentication. The hash is opaque to the
 * domain: hashing and verification belong to the security adapter.
 */
public record UserCredentials(User user, String passwordHash) {

    public UserCredentials {
        Objects.requireNonNull(user, "user must not be null");
        requireNotBlank(passwordHash, "passwordHash");
    }

    @Override
    public String toString() {
        return "UserCredentials[user=" + user + ", passwordHash=***]";
    }
}
