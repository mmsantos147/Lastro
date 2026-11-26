package io.matheusms.lastro.config;

import io.matheusms.lastro.domain.model.User;
import io.matheusms.lastro.domain.model.UserRole;
import io.matheusms.lastro.domain.port.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin when there are no users yet. There is no public sign-up: further users are
 * created by an admin.
 */
@Component
@EnableConfigurationProperties(BootstrapAdminProperties.class)
class InitialAdminBootstrap implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 12;

    private static final Logger log = LoggerFactory.getLogger(InitialAdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapAdminProperties properties;

    InitialAdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder, BootstrapAdminProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsAny()) {
            return;
        }
        if (!properties.isConfigured()) {
            log.warn("No users exist and LASTRO_ADMIN_USERNAME/LASTRO_ADMIN_PASSWORD are not set: nobody can log in");
            return;
        }
        if (properties.password().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "LASTRO_ADMIN_PASSWORD must have at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        var name = properties.name() == null || properties.name().isBlank() ? properties.username() : properties.name();
        var admin = users.create(
                new User(null, name, properties.username(), UserRole.ADMIN, true),
                passwordEncoder.encode(properties.password()));
        log.info("Created initial admin user '{}'", admin.username());
    }
}
