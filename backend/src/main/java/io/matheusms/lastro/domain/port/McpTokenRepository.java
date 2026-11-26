package io.matheusms.lastro.domain.port;

import io.matheusms.lastro.domain.model.McpToken;
import java.time.Instant;
import java.util.Optional;

public interface McpTokenRepository {

    McpToken save(McpToken token);

    Optional<McpToken> findByHash(String tokenHash);

    void deleteByHash(String tokenHash);

    /** Removes tokens expired at {@code now}. */
    void deleteExpired(Instant now);
}
