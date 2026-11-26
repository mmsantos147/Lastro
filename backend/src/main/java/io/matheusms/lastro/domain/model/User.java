package io.matheusms.lastro.domain.model;

import static io.matheusms.lastro.domain.model.Preconditions.requireNotBlank;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Owner of trades, transfers and distributions. The MVP has a single user, but personal data is always
 * scoped by user so others (family, friends) can be invited later. Personal documents (CPF, account numbers)
 * are intentionally not modeled. Credentials live outside the domain model (see {@link UserCredentials}).
 *
 * @param id {@code null} until persisted
 * @param username login name; stored in lower case
 */
public record User(Long id, String name, String username, UserRole role, boolean enabled) {

    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3,50}");

    public User {
        requireNotBlank(name, "name");
        requireNotBlank(username, "username");
        Objects.requireNonNull(role, "role must not be null");
        username = username.strip().toLowerCase();
        if (!USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException(
                    "Username must have 3-50 characters among letters, digits, '.', '_' and '-'");
        }
    }
}
