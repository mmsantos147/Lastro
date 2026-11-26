package io.matheusms.lastro.adapter.in.rest.security;

import io.matheusms.lastro.domain.model.UserCredentials;
import io.matheusms.lastro.domain.model.UserRole;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Principal stored in the HTTP session after login. The password hash is erased right after authentication
 * ({@link CredentialsContainer}), so it never lives in the session.
 */
public final class AuthenticatedUser implements UserDetails, CredentialsContainer {

    private final Long userId;
    private final String username;
    private final String name;
    private final UserRole role;
    private final boolean enabled;
    private String passwordHash;

    private AuthenticatedUser(
            Long userId, String username, String name, UserRole role, boolean enabled, String passwordHash) {
        this.userId = userId;
        this.username = username;
        this.name = name;
        this.role = role;
        this.enabled = enabled;
        this.passwordHash = passwordHash;
    }

    static AuthenticatedUser from(UserCredentials credentials) {
        var user = credentials.user();
        return new AuthenticatedUser(
                user.id(), user.username(), user.name(), user.role(), user.enabled(), credentials.passwordHash());
    }

    public Long userId() {
        return userId;
    }

    public String name() {
        return name;
    }

    public UserRole role() {
        return role;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
