package io.matheusms.lastro.adapter.in.mcp;

import io.matheusms.lastro.application.McpAuthorizationService;
import io.matheusms.lastro.application.McpAuthorizationService.InvalidGrantException;
import io.matheusms.lastro.application.McpAuthorizationService.TokenPair;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * OAuth 2.1 endpoints required by claude.ai to connect a custom MCP server (ADR 0011): discovery metadata (RFC 9728,
 * RFC 8414), client registration (RFC 7591), the authorization page and the token endpoint.
 * <p>
 * Clients are not stored: any client id is accepted, because what protects the flow is that codes are only sent to
 * the claude.ai callback URLs ({@link McpProperties#oauthRedirectUris()}), that the user types the password of the MCP
 * user, and PKCE. That also covers clients identified by a metadata document URL (CIMD) without fetching it.
 */
@Controller
@EnableConfigurationProperties(McpProperties.class)
class McpOAuthController {

    private static final String PKCE_METHOD = "S256";

    private final McpAuthorizationService authorization;
    private final McpProperties properties;
    private final String issuer;

    McpOAuthController(McpAuthorizationService authorization, McpProperties properties,
            @Value("${lastro.public-url}") String publicUrl) {
        this.authorization = authorization;
        this.properties = properties;
        this.issuer = publicUrl.replaceAll("/+$", "");
    }

    // --- Discovery ---

    @GetMapping(path = {"/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/mcp"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> protectedResource() {
        return ResponseEntity.ok(Map.of(
                "resource", issuer + "/mcp",
                "authorization_servers", List.of(issuer),
                "bearer_methods_supported", List.of("header")));
    }

    @GetMapping(path = {"/.well-known/oauth-authorization-server", "/.well-known/oauth-authorization-server/mcp"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> authorizationServer() {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("issuer", issuer);
        metadata.put("authorization_endpoint", issuer + "/oauth/authorize");
        metadata.put("token_endpoint", issuer + "/oauth/token");
        metadata.put("registration_endpoint", issuer + "/oauth/register");
        metadata.put("response_types_supported", List.of("code"));
        metadata.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
        metadata.put("code_challenge_methods_supported", List.of(PKCE_METHOD));
        metadata.put("token_endpoint_auth_methods_supported", List.of("none"));
        metadata.put("client_id_metadata_document_supported", true);
        return ResponseEntity.ok(metadata);
    }

    // --- Dynamic client registration ---

    @PostMapping(path = "/oauth/register", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> request) {
        var redirectUris = request.get("redirect_uris") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : List.<String>of();
        if (redirectUris.isEmpty() || !redirectUris.stream().allMatch(properties::allowsRedirectUri)) {
            return oauthError(HttpStatus.BAD_REQUEST, "invalid_redirect_uri",
                    "Only the claude.ai callback URLs are accepted");
        }
        var client = new LinkedHashMap<String, Object>();
        client.put("client_id", UUID.randomUUID().toString());
        client.put("client_id_issued_at", System.currentTimeMillis() / 1000);
        client.put("client_name", request.getOrDefault("client_name", "MCP client"));
        client.put("redirect_uris", redirectUris);
        client.put("grant_types", List.of("authorization_code", "refresh_token"));
        client.put("response_types", List.of("code"));
        client.put("token_endpoint_auth_method", "none");
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(client);
    }

    // --- Authorization page ---

    @GetMapping(path = "/oauth/authorize", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> authorizePage(@RequestParam MultiValueMap<String, String> params) {
        var request = AuthorizationRequest.from(params);
        if (!properties.allowsRedirectUri(request.redirectUri())) {
            // Never redirect to an unknown address: show the error here
            return page(HttpStatus.BAD_REQUEST, errorPage("Endereço de retorno não permitido."));
        }
        var invalid = request.validationError();
        if (invalid != null) {
            return redirectWithError(request, "invalid_request", invalid);
        }
        return page(HttpStatus.OK, loginPage(request, null));
    }

    /** The password is asked every time, so a forged form from another site cannot authorize anything (no CSRF). */
    @PostMapping(path = "/oauth/authorize", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> authorize(@RequestParam MultiValueMap<String, String> form) throws InterruptedException {
        var request = AuthorizationRequest.from(form);
        if (!properties.allowsRedirectUri(request.redirectUri())) {
            return page(HttpStatus.BAD_REQUEST, errorPage("Endereço de retorno não permitido."));
        }
        var invalid = request.validationError();
        if (invalid != null) {
            return redirectWithError(request, "invalid_request", invalid);
        }
        if (!"approve".equals(form.getFirst("decision"))) {
            return redirectWithError(request, "access_denied", "The user denied access");
        }
        var username = form.getFirst("username");
        var userId = properties.username() != null && properties.username().equalsIgnoreCase(strip(username))
                ? authorization.authenticate(username, form.getFirst("password"))
                : Optional.<Long>empty();
        if (userId.isEmpty()) {
            Thread.sleep(1000); // slows down password guessing
            return page(HttpStatus.UNAUTHORIZED, loginPage(request, "Usuário ou senha inválidos."));
        }
        var code = authorization.issueCode(userId.get(), request.clientId(), request.redirectUri(),
                request.codeChallenge());
        var location = UriComponentsBuilder.fromUriString(request.redirectUri())
                .queryParam("code", code)
                .queryParamIfPresent("state", Optional.ofNullable(request.state()))
                .queryParam("iss", issuer)
                .encode().build().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    // --- Token ---

    @PostMapping(path = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> token(@RequestParam MultiValueMap<String, String> form) {
        var grantType = form.getFirst("grant_type");
        var clientId = form.getFirst("client_id");
        if (clientId == null || clientId.isBlank()) {
            return oauthError(HttpStatus.UNAUTHORIZED, "invalid_client", "client_id is required");
        }
        try {
            TokenPair tokens;
            if ("authorization_code".equals(grantType)) {
                tokens = authorization.exchangeCode(form.getFirst("code"), clientId, form.getFirst("redirect_uri"),
                        form.getFirst("code_verifier"));
            } else if ("refresh_token".equals(grantType)) {
                tokens = authorization.refresh(form.getFirst("refresh_token"), clientId);
            } else {
                return oauthError(HttpStatus.BAD_REQUEST, "unsupported_grant_type", "Unsupported grant type");
            }
            var body = new LinkedHashMap<String, Object>();
            body.put("access_token", tokens.accessToken());
            body.put("token_type", "Bearer");
            body.put("expires_in", tokens.expiresIn());
            body.put("refresh_token", tokens.refreshToken());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
        } catch (InvalidGrantException exception) {
            return oauthError(HttpStatus.BAD_REQUEST, "invalid_grant", exception.getMessage());
        }
    }

    // --- Helpers ---

    private static ResponseEntity<Map<String, Object>> oauthError(HttpStatus status, String error, String detail) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(Map.of("error", error, "error_description", detail));
    }

    private ResponseEntity<String> redirectWithError(AuthorizationRequest request, String error, String detail) {
        var location = UriComponentsBuilder.fromUriString(request.redirectUri())
                .queryParam("error", error)
                .queryParam("error_description", detail)
                .queryParamIfPresent("state", Optional.ofNullable(request.state()))
                .encode().build().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    private static ResponseEntity<String> page(HttpStatus status, String html) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8)).body(html);
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    private String loginPage(AuthorizationRequest request, String error) {
        var hidden = new StringBuilder();
        request.asMap().forEach((name, value) -> {
            if (value != null) {
                hidden.append("<input type=\"hidden\" name=\"%s\" value=\"%s\">".formatted(name, escape(value)));
            }
        });
        var errorHtml = error == null ? "" : "<p class=\"error\" role=\"alert\">%s</p>".formatted(escape(error));
        return shell("""
                <h1>Conectar o Claude ao Lastro</h1>
                <p>O <strong>Claude</strong> (%s) quer ler sua carteira e salvar análises no Lastro. Entre com seu
                usuário do Lastro para autorizar.</p>
                %s
                <form method="post" action="/oauth/authorize">
                  %s
                  <label>Usuário<input name="username" autocomplete="username" required autofocus></label>
                  <label>Senha<input name="password" type="password" autocomplete="current-password" required></label>
                  <div class="actions">
                    <button type="submit" name="decision" value="deny" class="ghost" formnovalidate>Recusar</button>
                    <button type="submit" name="decision" value="approve">Autorizar</button>
                  </div>
                </form>
                """.formatted(escape(URI.create(request.redirectUri()).getHost()), errorHtml, hidden));
    }

    private static String errorPage(String message) {
        return shell("<h1>Não foi possível autorizar</h1><p>%s</p>".formatted(escape(message)));
    }

    private static String shell(String body) {
        return """
                <!doctype html>
                <html lang="pt-BR">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Autorizar acesso — Lastro</title>
                <style>
                  :root { color-scheme: light dark; --bg: #f9f9f7; --surface: #fcfcfb; --text: #0b0b0b;
                    --muted: #52514e; --border: #c3c2b7; --accent: #2a78d6; --danger: #b3261e; }
                  @media (prefers-color-scheme: dark) { :root { --bg: #0d0d0d; --surface: #1a1a19; --text: #f0efec;
                    --muted: #c3c2b7; --border: #383835; --accent: #3987e5; --danger: #f2b8b5; } }
                  body { margin: 0; min-height: 100vh; display: grid; place-items: center; padding: 16px;
                    background: var(--bg); color: var(--text); font: 16px/1.5 system-ui, sans-serif; }
                  main { width: min(100%%, 26rem); background: var(--surface); border: 1px solid var(--border);
                    border-radius: 12px; padding: 1.5rem; box-sizing: border-box; }
                  h1 { font-size: 1.25rem; margin: 0 0 0.75rem; }
                  label { display: grid; gap: 0.35rem; margin-top: 1rem; color: var(--muted); }
                  input { font: inherit; padding: 0.55rem 0.7rem; border: 1px solid var(--border); border-radius: 8px;
                    background: var(--bg); color: var(--text); }
                  .actions { display: flex; justify-content: flex-end; gap: 0.75rem; margin-top: 1.5rem; }
                  button { font: inherit; padding: 0.55rem 1rem; border-radius: 8px; border: 1px solid var(--accent);
                    background: var(--accent); color: #fff; cursor: pointer; }
                  button.ghost { background: transparent; color: var(--text); border-color: var(--border); }
                  .error { color: var(--danger); }
                </style>
                </head>
                <body><main>%s</main></body>
                </html>
                """.formatted(body);
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value);
    }

    /** Parameters of the authorization request, carried as hidden fields between the page and the POST. */
    private record AuthorizationRequest(String responseType, String clientId, String redirectUri, String state,
            String codeChallenge, String codeChallengeMethod, String scope, String resource) {

        static AuthorizationRequest from(MultiValueMap<String, String> params) {
            return new AuthorizationRequest(params.getFirst("response_type"), params.getFirst("client_id"),
                    params.getFirst("redirect_uri"), params.getFirst("state"), params.getFirst("code_challenge"),
                    params.getFirst("code_challenge_method"), params.getFirst("scope"), params.getFirst("resource"));
        }

        /** @return why the request cannot proceed, or {@code null} */
        String validationError() {
            if (!"code".equals(responseType)) {
                return "response_type must be code";
            }
            if (clientId == null || clientId.isBlank()) {
                return "client_id is required";
            }
            if (codeChallenge == null || codeChallenge.isBlank() || !PKCE_METHOD.equals(codeChallengeMethod)) {
                return "PKCE with S256 is required";
            }
            return null;
        }

        Map<String, String> asMap() {
            var map = new LinkedHashMap<String, String>();
            map.put("response_type", responseType);
            map.put("client_id", clientId);
            map.put("redirect_uri", redirectUri);
            map.put("state", state);
            map.put("code_challenge", codeChallenge);
            map.put("code_challenge_method", codeChallengeMethod);
            map.put("scope", scope);
            map.put("resource", resource);
            return map;
        }
    }
}
