package io.matheusms.lastro.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.matheusms.lastro.application.McpAuthorizationService;
import io.matheusms.lastro.domain.port.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SpaForwardingController.class)
@Import(SecurityConfig.class)
class SpaForwardingControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private UserRepository users;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private McpAuthorizationService mcpAuthorization;

    @Test
    void shouldForwardClientRoutesToTheFrontend() throws Exception {
        mvc.perform(get("/analises/5")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/operacoes")).andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void shouldServeBuiltFilesInsteadOfForwardingThem() throws Exception {
        mvc.perform(get("/assets/index-abc123.js")).andExpect(forwardedUrl(null));
        mvc.perform(get("/favicon.svg")).andExpect(forwardedUrl(null));
    }

    @Test
    void shouldNotForwardApiOrMcpRequests() throws Exception {
        mvc.perform(get("/api/unknown")).andExpect(status().isUnauthorized());
        mvc.perform(post("/mcp")).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldSendAContentSecurityPolicyThatBlocksInlineScripts() throws Exception {
        mvc.perform(get("/analises"))
                .andExpect(header().string("Content-Security-Policy", SecurityConfig.CONTENT_SECURITY_POLICY));
    }
}
