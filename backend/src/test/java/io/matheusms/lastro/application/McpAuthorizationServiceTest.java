package io.matheusms.lastro.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.matheusms.lastro.application.McpAuthorizationService.InvalidGrantException;
import io.matheusms.lastro.domain.model.McpToken;
import io.matheusms.lastro.domain.model.User;
import io.matheusms.lastro.domain.model.UserCredentials;
import io.matheusms.lastro.domain.model.UserRole;
import io.matheusms.lastro.domain.port.McpTokenRepository;
import io.matheusms.lastro.domain.port.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

class McpAuthorizationServiceTest {

    private static final Long USER_ID = 7L;
    private static final String CLIENT = "claude";
    private static final String REDIRECT = "https://claude.ai/api/mcp/auth_callback";
    private static final String VERIFIER = "a-long-random-code-verifier-0123456789abcdefghijklmnop";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-24T12:00:00Z"));
    private final InMemoryTokens tokens = new InMemoryTokens();
    private final UserRepository users = Mockito.mock(UserRepository.class);
    private McpAuthorizationService service;

    @BeforeEach
    void setUp() {
        var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        Mockito.when(users.findCredentialsByUsername("matheus")).thenReturn(Optional.of(new UserCredentials(
                new User(USER_ID, "Matheus", "matheus", UserRole.ADMIN, true), encoder.encode("right-password"))));
        service = new McpAuthorizationService(tokens, users, encoder, clock);
    }

    @Test
    void shouldAuthenticateOnlyWithTheRightPassword() {
        assertThat(service.authenticate("Matheus", "right-password")).contains(USER_ID);
        assertThat(service.authenticate("matheus", "wrong")).isEmpty();
        assertThat(service.authenticate("someone-else", "right-password")).isEmpty();
    }

    @Test
    void shouldExchangeACodeForTokensThatAuthenticateTheUser() {
        var code = service.issueCode(USER_ID, CLIENT, REDIRECT, McpAuthorizationService.challengeOf(VERIFIER));

        var pair = service.exchangeCode(code, CLIENT, REDIRECT, VERIFIER);

        assertThat(service.userOfAccessToken(pair.accessToken())).contains(USER_ID);
        assertThat(service.userOfAccessToken(pair.refreshToken())).isEmpty();
        assertThat(tokens.byHash.values()).noneMatch(token -> token.tokenHash().equals(pair.accessToken()));
    }

    @Test
    void shouldAcceptACodeOnlyOnce() {
        var code = service.issueCode(USER_ID, CLIENT, REDIRECT, McpAuthorizationService.challengeOf(VERIFIER));
        service.exchangeCode(code, CLIENT, REDIRECT, VERIFIER);

        assertThatThrownBy(() -> service.exchangeCode(code, CLIENT, REDIRECT, VERIFIER))
                .isInstanceOf(InvalidGrantException.class);
    }

    @Test
    void shouldRejectAWrongVerifierClientOrRedirect() {
        var challenge = McpAuthorizationService.challengeOf(VERIFIER);

        assertThatThrownBy(() -> service.exchangeCode(service.issueCode(USER_ID, CLIENT, REDIRECT, challenge),
                CLIENT, REDIRECT, "another-verifier")).isInstanceOf(InvalidGrantException.class);
        assertThatThrownBy(() -> service.exchangeCode(service.issueCode(USER_ID, CLIENT, REDIRECT, challenge),
                "other-client", REDIRECT, VERIFIER)).isInstanceOf(InvalidGrantException.class);
        assertThatThrownBy(() -> service.exchangeCode(service.issueCode(USER_ID, CLIENT, REDIRECT, challenge),
                CLIENT, "https://evil.example/callback", VERIFIER)).isInstanceOf(InvalidGrantException.class);
    }

    @Test
    void shouldRejectAnExpiredCode() {
        var code = service.issueCode(USER_ID, CLIENT, REDIRECT, McpAuthorizationService.challengeOf(VERIFIER));
        clock.advance(McpAuthorizationService.CODE_TTL);

        assertThatThrownBy(() -> service.exchangeCode(code, CLIENT, REDIRECT, VERIFIER))
                .isInstanceOf(InvalidGrantException.class);
    }

    @Test
    void shouldExpireAccessTokens() {
        var pair = tokensFor(CLIENT);
        clock.advance(McpAuthorizationService.ACCESS_TTL);

        assertThat(service.userOfAccessToken(pair.accessToken())).isEmpty();
    }

    @Test
    void shouldRotateRefreshTokens() {
        var first = tokensFor(CLIENT);
        clock.advance(McpAuthorizationService.ACCESS_TTL);

        var second = service.refresh(first.refreshToken(), CLIENT);

        assertThat(service.userOfAccessToken(second.accessToken())).contains(USER_ID);
        assertThatThrownBy(() -> service.refresh(first.refreshToken(), CLIENT))
                .isInstanceOf(InvalidGrantException.class);
        assertThatThrownBy(() -> service.refresh(second.refreshToken(), "other-client"))
                .isInstanceOf(InvalidGrantException.class);
    }

    @Test
    void shouldMatchTheChallengeOfTheRfc7636Example() {
        assertThat(McpAuthorizationService.challengeOf("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    private McpAuthorizationService.TokenPair tokensFor(String client) {
        var code = service.issueCode(USER_ID, client, REDIRECT, McpAuthorizationService.challengeOf(VERIFIER));
        return service.exchangeCode(code, client, REDIRECT, VERIFIER);
    }

    private static final class InMemoryTokens implements McpTokenRepository {

        private final Map<String, McpToken> byHash = new HashMap<>();

        @Override
        public McpToken save(McpToken token) {
            byHash.put(token.tokenHash(), token);
            return token;
        }

        @Override
        public Optional<McpToken> findByHash(String tokenHash) {
            return Optional.ofNullable(byHash.get(tokenHash));
        }

        @Override
        public void deleteByHash(String tokenHash) {
            byHash.remove(tokenHash);
        }

        @Override
        public void deleteExpired(Instant now) {
            byHash.values().removeIf(token -> token.isExpiredAt(now));
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
