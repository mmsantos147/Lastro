package io.matheusms.lastro.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface McpTokenJpaRepository extends JpaRepository<McpTokenEntity, Long> {

    Optional<McpTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from McpTokenEntity t where t.tokenHash = :tokenHash")
    void deleteByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from McpTokenEntity t where t.expiresAt <= :now")
    void deleteExpired(Instant now);
}
