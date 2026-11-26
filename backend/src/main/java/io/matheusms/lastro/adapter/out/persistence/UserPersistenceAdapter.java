package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.User;
import io.matheusms.lastro.domain.model.UserCredentials;
import io.matheusms.lastro.domain.port.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class UserPersistenceAdapter implements UserRepository {

    private final UserJpaRepository repository;

    UserPersistenceAdapter(UserJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public User create(User user, String passwordHash) {
        if (user.id() != null) {
            throw new IllegalArgumentException("User already persisted: " + user.id());
        }
        return repository.save(UserEntity.from(user, passwordHash)).toDomain();
    }

    @Override
    public Optional<User> findById(Long id) {
        return repository.findById(id).map(UserEntity::toDomain);
    }

    @Override
    public Optional<UserCredentials> findCredentialsByUsername(String username) {
        // Usernames are stored in lower case (see User)
        return repository.findByUsername(username.strip().toLowerCase()).map(UserEntity::toCredentials);
    }

    @Override
    public boolean existsAny() {
        return repository.count() > 0;
    }
}
