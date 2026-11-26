package io.matheusms.lastro.adapter.in.mcp;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param token fixed secret accepted as {@code Authorization: Bearer <token>} or {@code X-Auth-Token: <token>}
 *     (Claude Code, local tests); MCP is closed when blank
 * @param username user whose portfolio the tools read and write; also the only user who can authorize an OAuth
 *     client (claude.ai)
 * @param oauthRedirectUris where authorization codes may be sent: the callback URLs of claude.ai (ADR 0011)
 */
@ConfigurationProperties("lastro.mcp")
public record McpProperties(String token, String username, List<String> oauthRedirectUris) {

    public McpProperties {
        oauthRedirectUris = oauthRedirectUris == null ? List.of() : List.copyOf(oauthRedirectUris);
    }

    public boolean isConfigured() {
        return token != null && !token.isBlank() && username != null && !username.isBlank();
    }

    public boolean allowsRedirectUri(String redirectUri) {
        return redirectUri != null && oauthRedirectUris.contains(redirectUri);
    }
}
