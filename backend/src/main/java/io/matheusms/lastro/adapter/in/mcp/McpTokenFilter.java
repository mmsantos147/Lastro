package io.matheusms.lastro.adapter.in.mcp;

import io.matheusms.lastro.application.McpAuthorizationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates MCP requests (personal use) with either:
 * <ul>
 *   <li>an OAuth access token issued by {@link McpOAuthController} (claude.ai connectors, ADR 0011); or</li>
 *   <li>the fixed token, in {@code X-Auth-Token} or {@code Authorization: Bearer} (Claude Code, local tests).</li>
 * </ul>
 * Requests without a valid token get 401 with the {@code resource_metadata} pointer that starts the OAuth discovery,
 * and never reach the tools. The fixed token is compared in constant time.
 */
public class McpTokenFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";
    static final String TOKEN_HEADER = "X-Auth-Token";

    private final McpProperties properties;
    private final McpAuthorizationService authorization;
    private final String challenge;

    /** @param resourceMetadataUrl absolute URL of {@code /.well-known/oauth-protected-resource} */
    public McpTokenFilter(McpProperties properties, McpAuthorizationService authorization, String resourceMetadataUrl) {
        this.properties = properties;
        this.authorization = authorization;
        this.challenge = "Bearer resource_metadata=\"%s\"".formatted(resourceMetadataUrl);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var tokens = providedTokens(request);
        if (!properties.isConfigured() || tokens.stream().noneMatch(this::isValid)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge);
            return;
        }
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                properties.username(), null, List.of(new SimpleGrantedAuthority("ROLE_MCP")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** Only the MCP user can authorize a client, so an OAuth token always acts on that user's portfolio. */
    private boolean isValid(String token) {
        return matches(token, properties.token()) || authorization.userOfAccessToken(token).isPresent();
    }

    /**
     * Tokens from {@code X-Auth-Token} and {@code Authorization: Bearer}; any valid one is enough. claude.ai sends its
     * OAuth token in {@code Authorization} and custom headers (such as a fixed token) alongside.
     */
    private static List<String> providedTokens(HttpServletRequest request) {
        var tokens = new java.util.ArrayList<String>();
        var custom = request.getHeader(TOKEN_HEADER);
        if (custom != null && !custom.isBlank()) {
            tokens.add(custom.strip());
        }
        var authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith(BEARER)
                && !authorization.substring(BEARER.length()).isBlank()) {
            tokens.add(authorization.substring(BEARER.length()).strip());
        }
        return tokens;
    }

    private static boolean matches(String provided, String expected) {
        return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}
