package io.matheusms.lastro;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end login as the frontend does it, against the real database: the admin is created by the
 * bootstrap from the properties below, then logs in with the CSRF cookie echoed in the header.
 */
@SpringBootTest(properties = {
        "lastro.bootstrap-admin.username=admin",
        "lastro.bootstrap-admin.password=integration-test-password"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthenticationFlowIT {

    @Autowired
    private MockMvc mvc;

    @Test
    void shouldLoginWithBootstrappedAdminAndAccessTheApi() throws Exception {
        var session = new MockHttpSession();
        var csrfCookie = mvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false)) // the frontend must read it
                .andReturn().getResponse().getCookie("XSRF-TOKEN");

        var loginResult = mvc.perform(post("/api/auth/login")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .param("username", "admin")
                        .param("password", "integration-test-password"))
                .andExpect(status().isNoContent())
                .andReturn();
        var authenticatedSession = (MockHttpSession) loginResult.getRequest().getSession();

        mvc.perform(get("/api/auth/me").session(authenticatedSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void shouldExposeHealthWithoutAuthentication() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
