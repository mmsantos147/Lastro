package io.matheusms.lastro.adapter.in.mcp;

import io.matheusms.lastro.domain.port.UserRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** The user configured for MCP ({@code LASTRO_MCP_USERNAME}): every tool works on this user's portfolio. */
@Component
@EnableConfigurationProperties(McpProperties.class)
class McpUser {

    private final UserRepository users;
    private final McpProperties properties;

    McpUser(UserRepository users, McpProperties properties) {
        this.users = users;
        this.properties = properties;
    }

    Long id() {
        return users.findCredentialsByUsername(properties.username())
                .filter(credentials -> credentials.user().enabled())
                .map(credentials -> credentials.user().id())
                .orElseThrow(() -> new IllegalStateException("The MCP user does not exist or is disabled"));
    }
}
