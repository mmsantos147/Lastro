package io.matheusms.lastro.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credentials of the first admin, created on startup when the database has no users. Supplied through
 * environment variables only (see application.yml); never committed.
 */
@ConfigurationProperties("lastro.bootstrap-admin")
public record BootstrapAdminProperties(String username, String password, String name) {

    boolean isConfigured() {
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }
}
