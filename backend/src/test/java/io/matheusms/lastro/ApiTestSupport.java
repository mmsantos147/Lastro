package io.matheusms.lastro;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.matheusms.lastro.domain.model.User;
import io.matheusms.lastro.domain.model.UserRole;
import io.matheusms.lastro.domain.port.UserRepository;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Builds authenticated API requests for a fresh user, so integration tests never see each other's data. */
public final class ApiTestSupport {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final UserDetails principal;

    public ApiTestSupport(UserRepository users, UserDetailsService userDetailsService) {
        var username = "api-user-" + SEQUENCE.incrementAndGet() + "-" + System.nanoTime();
        users.create(new User(null, "API test user", username, UserRole.ADMIN, true), "{noop}irrelevant");
        this.principal = userDetailsService.loadUserByUsername(username);
    }

    /** Requests as an existing user. */
    public ApiTestSupport(UserRepository users, UserDetailsService userDetailsService, String existingUsername) {
        this.principal = userDetailsService.loadUserByUsername(existingUsername);
    }

    public MockHttpServletRequestBuilder getJson(String path, Object... variables) {
        return get(path, variables).with(user(principal));
    }

    public MockHttpServletRequestBuilder postJson(String path, String body) {
        return post(path).with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    public MockHttpServletRequestBuilder deleteJson(String path, Object... variables) {
        return delete(path, variables).with(user(principal)).with(csrf());
    }

    /** A unique suffix for codes that must be unique across tests sharing the database (e.g. asset codes). */
    public static String unique(String prefix) {
        return prefix + SEQUENCE.incrementAndGet();
    }
}
