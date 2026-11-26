package io.matheusms.lastro.domain.port;

import io.matheusms.lastro.domain.model.User;
import io.matheusms.lastro.domain.model.UserCredentials;
import java.util.Optional;

public interface UserRepository {

    /** Persists a new user with an already hashed password. */
    User create(User user, String passwordHash);

    Optional<User> findById(Long id);

    /** @param username case-insensitive */
    Optional<UserCredentials> findCredentialsByUsername(String username);

    boolean existsAny();
}
