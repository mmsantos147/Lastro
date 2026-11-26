package io.matheusms.lastro.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * OAuth token issued to an MCP client (claude.ai) after the user authorized it (ADR 0011). Only the SHA-256 of the
 * token is stored, so a database leak does not expose usable tokens.
 *
 * @param id {@code null} until persisted
 * @param tokenHash SHA-256 of the token, in hex
 * @param clientId client the token was issued to; a refresh token only works for the same client
 */
public record McpToken(Long id, String tokenHash, Kind kind, Long userId, String clientId, Instant expiresAt) {

    public McpToken {
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public enum Kind {
        /** Sent with every MCP request; short-lived. */
        ACCESS,
        /** Exchanged for a new access token (and a new refresh token) when the access token expires. */
        REFRESH
    }
}
