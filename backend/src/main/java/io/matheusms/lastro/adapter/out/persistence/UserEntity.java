package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.User;
import io.matheusms.lastro.domain.model.UserCredentials;
import io.matheusms.lastro.domain.model.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserRole role;

    @Column(nullable = false)
    private boolean enabled;

    protected UserEntity() {
    }

    static UserEntity from(User user, String passwordHash) {
        var entity = new UserEntity();
        entity.id = user.id();
        entity.name = user.name();
        entity.username = user.username();
        entity.passwordHash = passwordHash;
        entity.role = user.role();
        entity.enabled = user.enabled();
        return entity;
    }

    User toDomain() {
        return new User(id, name, username, role, enabled);
    }

    UserCredentials toCredentials() {
        return new UserCredentials(toDomain(), passwordHash);
    }
}
