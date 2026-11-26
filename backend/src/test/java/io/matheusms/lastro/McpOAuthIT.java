package io.matheusms.lastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.matheusms.lastro.domain.model.User;
import io.matheusms.lastro.domain.model.UserRole;
import io.matheusms.lastro.domain.port.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

/** The OAuth flow claude.ai runs to connect the MCP server: discovery, registration, login, code, token, tools. */
@SpringBootTest(properties = {
        "lastro.mcp.token=test-mcp-token",
        "lastro.mcp.username=oauth-owner",
        "lastro.public-url=https://lastro.test/"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class McpOAuthIT {

    private static final String REDIRECT = "https://claude.ai/api/mcp/auth_callback";
    private static final String VERIFIER = "verifier-0123456789-abcdefghijklmnopqrstuvwxyz-ABCDEFGHIJ";
    private static final String PASSWORD = "a-strong-password";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void createOwner() {
        if (users.findCredentialsByUsername("oauth-owner").isEmpty()) {
            users.create(new User(null, "OAuth owner", "oauth-owner", UserRole.ADMIN, true),
                    passwordEncoder.encode(PASSWORD));
        }
    }

    @Test
    void shouldPointUnauthenticatedClientsToTheDiscoveryMetadata() throws Exception {
        mvc.perform(rpc()).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate",
                        "Bearer resource_metadata=\"https://lastro.test/.well-known/oauth-protected-resource\""));
        mvc.perform(get("/.well-known/oauth-protected-resource")).andExpect(status().isOk())
                .andExpect(jsonPath("$.resource").value("https://lastro.test/mcp"))
                .andExpect(jsonPath("$.authorization_servers[0]").value("https://lastro.test"));
        mvc.perform(get("/.well-known/oauth-authorization-server")).andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("https://lastro.test"))
                .andExpect(jsonPath("$.token_endpoint").value("https://lastro.test/oauth/token"))
                .andExpect(jsonPath("$.code_challenge_methods_supported[0]").value("S256"));
    }

    @Test
    void shouldConnectThroughTheAuthorizationCodeFlow() throws Exception {
        var registration = mvc.perform(post("/oauth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client_name\": \"Claude\", \"redirect_uris\": [\"%s\"]}".formatted(REDIRECT)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var clientId = json.readTree(registration).path("client_id").asString();
        var challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(StandardCharsets.US_ASCII)));

        mvc.perform(get("/oauth/authorize").param("response_type", "code").param("client_id", clientId)
                        .param("redirect_uri", REDIRECT).param("state", "xyz").param("code_challenge", challenge)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("https://claude.ai")));

        var location = mvc.perform(post("/oauth/authorize").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("response_type", "code").param("client_id", clientId).param("redirect_uri", REDIRECT)
                        .param("state", "xyz").param("code_challenge", challenge).param("code_challenge_method", "S256")
                        .param("username", "oauth-owner").param("password", PASSWORD).param("decision", "approve"))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).startsWith(REDIRECT).contains("state=xyz");
        var code = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");

        var tokenResponse = mvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code").param("code", code).param("client_id", clientId)
                        .param("redirect_uri", REDIRECT).param("code_verifier", VERIFIER))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var accessToken = json.readTree(tokenResponse).path("access_token").asString();

        mvc.perform(rpc().header("Authorization", "Bearer " + accessToken)).andExpect(status().isOk());
    }

    @Test
    void shouldNeverSendCodesToUnknownRedirectUris() throws Exception {
        mvc.perform(get("/oauth/authorize").param("response_type", "code").param("client_id", "any")
                        .param("redirect_uri", "https://evil.example/callback").param("code_challenge", "x")
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/oauth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"redirect_uris\": [\"https://evil.example/callback\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectAWrongPassword() throws Exception {
        mvc.perform(post("/oauth/authorize").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("response_type", "code").param("client_id", "claude").param("redirect_uri", REDIRECT)
                        .param("code_challenge", "challenge").param("code_challenge_method", "S256")
                        .param("username", "oauth-owner").param("password", "wrong").param("decision", "approve"))
                .andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder rpc() {
        return post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content("{\"jsonrpc\": \"2.0\", \"id\": 1, \"method\": \"tools/list\", \"params\": {}}");
    }
}
