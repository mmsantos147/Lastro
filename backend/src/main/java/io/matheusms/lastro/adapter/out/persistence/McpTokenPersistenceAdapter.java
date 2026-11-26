package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.McpToken;
import io.matheusms.lastro.domain.port.McpTokenRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class McpTokenPersistenceAdapter implements McpTokenRepository {

    private final McpTokenJpaRepository repository;

    McpTokenPersistenceAdapter(McpTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public McpToken save(McpToken token) {
        return repository.save(McpTokenEntity.from(token)).toDomain();
    }

    @Override
    public Optional<McpToken> findByHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(McpTokenEntity::toDomain);
    }

    @Override
    public void deleteByHash(String tokenHash) {
        repository.deleteByTokenHash(tokenHash);
    }

    @Override
    public void deleteExpired(Instant now) {
        repository.deleteExpired(now);
    }
}
