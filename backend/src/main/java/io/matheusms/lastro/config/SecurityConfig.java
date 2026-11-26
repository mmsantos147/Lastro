package io.matheusms.lastro.config;

import io.matheusms.lastro.adapter.in.mcp.McpProperties;
import io.matheusms.lastro.adapter.in.mcp.McpTokenFilter;
import io.matheusms.lastro.application.McpAuthorizationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Session-based authentication for the frontend (ADR 0003). The API lives under {@code /api} and answers with
 * status codes instead of redirects; everything else is the frontend's static content, which is public.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(McpProperties.class)
public class SecurityConfig {

    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; "
            + "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    /**
     * MCP endpoint used by Claude: no session, no CSRF (not a browser), only the fixed bearer token (ADR 0007).
     * Evaluated before the frontend chain.
     */
    @Bean
    @Order(1)
    SecurityFilterChain mcpFilterChain(HttpSecurity http, McpProperties mcpProperties,
            McpAuthorizationService authorization, @Value("${lastro.public-url}") String publicUrl) throws Exception {
        var resourceMetadata = publicUrl.replaceAll("/+$", "") + "/.well-known/oauth-protected-resource";
        http
                .securityMatcher("/mcp", "/mcp/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new McpTokenFilter(mcpProperties, authorization, resourceMetadata),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /**
     * OAuth for MCP clients (ADR 0011): public discovery, registration, token and authorization page. No session and
     * no CSRF token: the authorization form asks for the password every time, so a forged submission gets nowhere.
     * The page may only post to itself, and after the user approves the browser follows a redirect to claude.ai,
     * which {@code form-action} must allow.
     */
    @Bean
    @Order(0)
    SecurityFilterChain mcpOAuthFilterChain(HttpSecurity http, McpProperties mcpProperties) throws Exception {
        var redirectOrigins = mcpProperties.oauthRedirectUris().stream()
                .map(uri -> java.net.URI.create(uri).resolve("/").toString().replaceAll("/$", ""))
                .distinct()
                .collect(java.util.stream.Collectors.joining(" "));
        http
                .securityMatcher("/.well-known/oauth-protected-resource/**", "/.well-known/oauth-authorization-server/**",
                        "/.well-known/oauth-protected-resource", "/.well-known/oauth-authorization-server",
                        "/oauth/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; "
                                + "style-src 'unsafe-inline'; form-action 'self' " + redirectOrigins
                                + "; base-uri 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").denyAll()
                        .anyRequest().permitAll())
                // Cookie-based CSRF token readable by the SPA (XSRF-TOKEN cookie -> X-XSRF-TOKEN header)
                .csrf(csrf -> csrf.spa())
                .formLogin(form -> form
                        .loginPage("/login") // frontend route; disables Spring's generated login page
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value()))
                        .failureHandler((request, response, exception) ->
                                response.setStatus(HttpStatus.UNAUTHORIZED.value()))
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .httpBasic(AbstractHttpConfigurer::disable)
                // Defense in depth against XSS (e.g. in analyses written by the LLM): only same-origin scripts.
                // Inline styles are allowed because the chart library sets style attributes.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)));
        return http.build();
    }

    /** Stores the algorithm with the hash (e.g. {@code {bcrypt}...}), so it can be upgraded later. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
