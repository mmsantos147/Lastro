package io.matheusms.lastro.adapter.in.rest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.matheusms.lastro.config.SecurityConfig;
import io.matheusms.lastro.domain.model.User;
import io.matheusms.lastro.domain.model.UserCredentials;
import io.matheusms.lastro.domain.model.UserRole;
import io.matheusms.lastro.domain.port.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * HTTP behavior of the session authentication. The CSRF cookie itself is checked in {@code AuthenticationFlowIT}:
 * the {@code csrf()} post-processor used here swaps the CSRF repository of this context for a test double.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, DatabaseUserDetailsService.class})
class AuthEndpointsTest {

    private static final String PASSWORD = "correct horse battery";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private UserRepository users;
    @MockitoBean
    private io.matheusms.lastro.application.McpAuthorizationService mcpAuthorization;

    @BeforeEach
    void setUp() {
        given(users.findCredentialsByUsername("matheus"))
                .willReturn(Optional.of(credentials(new User(1L, "Matheus", "matheus", UserRole.ADMIN, true))));
        given(users.findCredentialsByUsername("disabled"))
                .willReturn(Optional.of(credentials(new User(2L, "Disabled", "disabled", UserRole.USER, false))));
    }

    @Test
    void shouldAnswer401WithoutRedirectForUnauthenticatedApiRequests() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void shouldLoginWithValidCredentials() throws Exception {
        mvc.perform(login("matheus", PASSWORD).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(authenticated().withUsername("matheus").withRoles("ADMIN"));
    }

    @Test
    void shouldRejectLoginWithoutCsrfToken() throws Exception {
        mvc.perform(login("matheus", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(unauthenticated());
    }

    @Test
    void shouldRejectWrongPassword() throws Exception {
        mvc.perform(login("matheus", "wrong password").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(unauthenticated());
    }

    @Test
    void shouldRejectUnknownUser() throws Exception {
        mvc.perform(login("nobody", PASSWORD).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(unauthenticated());
    }

    @Test
    void shouldRejectDisabledUser() throws Exception {
        mvc.perform(login("disabled", PASSWORD).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(unauthenticated());
    }

    @Test
    void shouldChangeSessionIdOnLoginToPreventSessionFixation() throws Exception {
        // Simulates a session id planted by an attacker and sent by the victim's browser
        var plantedSession = new MockHttpSession();
        var plantedId = plantedSession.getId();

        mvc.perform(login("matheus", PASSWORD).with(csrf()).session(plantedSession)
                        .with(request -> {
                            request.setRequestedSessionId(plantedId);
                            request.setRequestedSessionIdValid(true);
                            return request;
                        }))
                .andExpect(status().isNoContent());

        assertThat(plantedSession.getId()).isNotEqualTo(plantedId);
    }

    @Test
    void shouldReturnCurrentUserWithoutSecrets() throws Exception {
        var principal = AuthenticatedUser.from(credentials(new User(1L, "Matheus", "matheus", UserRole.ADMIN, true)));

        mvc.perform(get("/api/auth/me").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("matheus"))
                .andExpect(jsonPath("$.name").value("Matheus"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void shouldLogout() throws Exception {
        mvc.perform(post("/api/auth/logout").with(user("matheus")).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(unauthenticated());
    }

    @Test
    void shouldCloseActuatorEndpointsOtherThanHealth() throws Exception {
        mvc.perform(get("/actuator/env").with(user("matheus")))
                .andExpect(status().isForbidden());
    }

    private UserCredentials credentials(User user) {
        return new UserCredentials(user, passwordEncoder.encode(PASSWORD));
    }

    private static MockHttpServletRequestBuilder login(
            String username, String password) {
        return post("/api/auth/login").param("username", username).param("password", password);
    }
}
