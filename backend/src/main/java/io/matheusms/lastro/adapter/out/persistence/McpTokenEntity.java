package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.McpToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "mcp_token")
class McpTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private McpToken.Kind kind;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "client_id", nullable = false, length = 255)
    private String clientId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected McpTokenEntity() {
    }

    static McpTokenEntity from(McpToken token) {
        var entity = new McpTokenEntity();
        entity.id = token.id();
        entity.tokenHash = token.tokenHash();
        entity.kind = token.kind();
        entity.userId = token.userId();
        entity.clientId = token.clientId();
        entity.expiresAt = token.expiresAt();
        return entity;
    }

    McpToken toDomain() {
        return new McpToken(id, tokenHash, kind, userId, clientId, expiresAt);
    }
}
