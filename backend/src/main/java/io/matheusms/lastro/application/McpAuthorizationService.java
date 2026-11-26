package io.matheusms.lastro.application;

import io.matheusms.lastro.domain.model.McpToken;
import io.matheusms.lastro.domain.port.McpTokenRepository;
import io.matheusms.lastro.domain.port.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OAuth 2.1 authorization code flow with PKCE for MCP clients (ADR 0011): the user logs in on the authorization page,
 * the client gets a one-time code and exchanges it for an access token and a refresh token. Codes live in memory for
 * a few minutes (a restart only asks the user to authorize again); tokens are stored as hashes.
 */
@Service
public class McpAuthorizationService {

    static final Duration CODE_TTL = Duration.ofMinutes(5);
    static final Duration ACCESS_TTL = Duration.ofHours(1);
    static final Duration REFRESH_TTL = Duration.ofDays(90);

    private final McpTokenRepository tokens;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, PendingCode> codes = new ConcurrentHashMap<>();

    McpAuthorizationService(McpTokenRepository tokens, UserRepository users, PasswordEncoder passwordEncoder,
            Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** @return the id of the enabled user with these credentials */
    @Transactional(readOnly = true)
    public Optional<Long> authenticate(String username, String password) {
        if (username == null || password == null) {
            return Optional.empty();
        }
        return users.findCredentialsByUsername(username.strip().toLowerCase())
                .filter(credentials -> credentials.user().enabled())
                .filter(credentials -> passwordEncoder.matches(password, credentials.passwordHash()))
                .map(credentials -> credentials.user().id());
    }

    /**
     * @param codeChallenge PKCE challenge: base64url of the SHA-256 of the verifier the client keeps
     * @return the code to send back to the client's redirect URI
     */
    public String issueCode(Long userId, String clientId, String redirectUri, String codeChallenge) {
        var now = Instant.now(clock);
        codes.values().removeIf(pending -> !now.isBefore(pending.expiresAt()));
        var code = newSecret();
        codes.put(sha256(code), new PendingCode(userId, clientId, redirectUri, codeChallenge, now.plus(CODE_TTL)));
        return code;
    }

    /**
     * Exchanges a code for tokens. A code works once, for the same client and redirect URI, before it expires, and
     * only with the verifier that matches its challenge.
     *
     * @throws InvalidGrantException otherwise
     */
    @Transactional
    public TokenPair exchangeCode(String code, String clientId, String redirectUri, String codeVerifier) {
        var pending = code == null ? null : codes.remove(sha256(code));
        if (pending == null || !Instant.now(clock).isBefore(pending.expiresAt())) {
            throw new InvalidGrantException("Unknown or expired authorization code");
        }
        if (!pending.clientId().equals(clientId) || !pending.redirectUri().equals(redirectUri)) {
            throw new InvalidGrantException("The code was issued to another client or redirect URI");
        }
        if (codeVerifier == null || !pending.codeChallenge().equals(challengeOf(codeVerifier))) {
            throw new InvalidGrantException("The code verifier does not match the challenge");
        }
        return issueTokens(pending.userId(), clientId);
    }

    /**
     * Rotates a refresh token: the old one stops working and a new pair is issued.
     *
     * @throws InvalidGrantException if the token is unknown, expired or from another client
     */
    @Transactional
    public TokenPair refresh(String refreshToken, String clientId) {
        var hash = refreshToken == null ? null : sha256(refreshToken);
        var stored = hash == null ? Optional.<McpToken>empty() : tokens.findByHash(hash);
        var token = stored.filter(found -> found.kind() == McpToken.Kind.REFRESH)
                .filter(found -> !found.isExpiredAt(Instant.now(clock)))
                .filter(found -> found.clientId().equals(clientId))
                .orElseThrow(() -> new InvalidGrantException("Unknown or expired refresh token"));
        tokens.deleteByHash(hash);
        return issueTokens(token.userId(), clientId);
    }

    /** @return the user an unexpired access token was issued to */
    @Transactional(readOnly = true)
    public Optional<Long> userOfAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return Optional.empty();
        }
        return tokens.findByHash(sha256(accessToken))
                .filter(token -> token.kind() == McpToken.Kind.ACCESS)
                .filter(token -> !token.isExpiredAt(Instant.now(clock)))
                .map(McpToken::userId);
    }

    private TokenPair issueTokens(Long userId, String clientId) {
        var now = Instant.now(clock);
        tokens.deleteExpired(now);
        var access = newSecret();
        var refresh = newSecret();
        tokens.save(new McpToken(null, sha256(access), McpToken.Kind.ACCESS, userId, clientId, now.plus(ACCESS_TTL)));
        tokens.save(new McpToken(null, sha256(refresh), McpToken.Kind.REFRESH, userId, clientId,
                now.plus(REFRESH_TTL)));
        return new TokenPair(access, refresh, ACCESS_TTL.toSeconds());
    }

    private String newSecret() {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** PKCE S256: {@code base64url(sha256(verifier))} without padding. */
    static String challengeOf(String codeVerifier) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest(codeVerifier));
    }

    private static String sha256(String value) {
        return HexFormat.of().formatHex(digest(value));
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is always available", exception);
        }
    }

    private record PendingCode(Long userId, String clientId, String redirectUri, String codeChallenge,
            Instant expiresAt) {
    }

    /** @param expiresIn lifetime of the access token, in seconds */
    public record TokenPair(String accessToken, String refreshToken, long expiresIn) {
    }

    /** The code or refresh token cannot be used (OAuth error {@code invalid_grant}). */
    public static class InvalidGrantException extends RuntimeException {

        public InvalidGrantException(String message) {
            super(message);
        }
    }
}
