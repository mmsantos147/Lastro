package io.matheusms.lastro;

import static io.matheusms.lastro.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.matheusms.lastro.domain.model.User;
import io.matheusms.lastro.domain.model.UserRole;
import io.matheusms.lastro.domain.port.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The MCP server as Claude uses it (JSON-RPC over Streamable HTTP, stateless): token check, tool list, the
 * suggestion → save_analysis flow and the analysis shown on the website.
 */
@SpringBootTest(properties = {
        "lastro.mcp.token=test-mcp-token",
        "lastro.mcp.username=mcp-owner",
        "lastro.public-url=https://lastro.test/"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class McpServerIT {

    private static final String TOKEN = "test-mcp-token";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private UserDetailsService userDetailsService;

    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void createMcpOwner() {
        if (users.findCredentialsByUsername("mcp-owner").isEmpty()) {
            users.create(new User(null, "MCP owner", "mcp-owner", UserRole.ADMIN, true), "{noop}irrelevant");
        }
    }

    @Test
    void shouldRejectRequestsWithoutTheToken() throws Exception {
        mvc.perform(rpc("tools/list", "{}").header("Authorization", "")).andExpect(status().isUnauthorized());
        mvc.perform(rpc("tools/list", "{}").header("Authorization", "Bearer wrong-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(rpc("tools/list", "{}").header("X-Auth-Token", "wrong-token"))
                .andExpect(status().isUnauthorized());
    }

    /** claude.ai keeps Authorization for its OAuth; custom connectors send the token in X-Auth-Token. */
    @Test
    void shouldAcceptTheTokenInTheXAuthTokenHeader() throws Exception {
        mvc.perform(rpc("tools/list", "{}").header("X-Auth-Token", TOKEN)).andExpect(status().isOk());
    }

    @Test
    void shouldListThePortfolioTools() throws Exception {
        var result = call("tools/list", "{}");

        assertThat(result.path("tools").findValuesAsString("name")).containsExactlyInAnyOrder(
                "portfolio_summary", "list_positions", "allocation_vs_target", "suggest_contribution",
                "distribution_history", "save_analysis");
    }

    @Test
    void shouldExposeTheMonthlyAnalysisPrompt() throws Exception {
        var result = call("prompts/get", "{\"name\": \"monthly_analysis\", \"arguments\": {\"amount\": \"2500\"}}");

        var text = result.path("messages").path(0).path("content").path("text").asString();
        assertThat(text).contains("Prompt: análise mensal da carteira").endsWith("aporte de R$ 2500.");
    }

    @Test
    void shouldSaveAnAnalysisLinkedToTheSuggestionAndShowItOnTheWebsite() throws Exception {
        var owner = new ApiTestSupport(users, userDetailsService, "mcp-owner");
        mvc.perform(owner.postJson("/api/strategies", """
                {"name": "MCP %s", "active": true, "targets": [{"assetClass": "ETF", "targetPercentage": 100}]}"""
                .formatted(unique("S")))).andExpect(status().isCreated());

        var suggestion = toolResult("suggest_contribution", "{\"amount\": 1500}");
        assertThat(suggestion.path("items").path(0).path("reason").asString()).isEqualTo("NEW_POSITION_IN_CLASS");
        var suggestionId = suggestion.path("suggestion_id").asLong();

        var saved = toolResult("save_analysis", """
                {"title": "Aporte de novembro", "text": "## Leitura\\n\\nTudo **ok**.", "amount": 1500,
                 "suggestion_id": %d}""".formatted(suggestionId));

        assertThat(saved.path("url").asString()).isEqualTo("https://lastro.test/analises/" + saved.path("id").asLong());
        assertThat(saved.path("warning").isNull()).isTrue();
        mvc.perform(owner.getJson("/api/analyses/{id}", saved.path("id").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Aporte de novembro"))
                .andExpect(jsonPath("$.suggestion.items", hasSize(1)));
    }

    @Test
    void shouldSaveWithoutLinkWhenTheSuggestionIsUnknown() throws Exception {
        var saved = toolResult("save_analysis", """
                {"title": "Exploração", "text": "Texto", "suggestion_id": 999999}""");

        assertThat(saved.path("suggestion_id").isNull()).isTrue();
        assertThat(saved.path("warning").asString()).contains("not found");
    }

    @Test
    void shouldReportValidationErrorsAsToolErrors() throws Exception {
        var response = call("tools/call", """
                {"name": "save_analysis", "arguments": {"title": "%s", "text": "x"}}""".formatted("t".repeat(121)));

        assertThat(response.path("isError").asBoolean()).isTrue();
        assertThat(response.path("content").path(0).path("text").asString()).contains("120");
    }

    private JsonNode toolResult(String tool, String arguments) throws Exception {
        var response = call("tools/call", "{\"name\": \"%s\", \"arguments\": %s}".formatted(tool, arguments));
        assertThat(response.path("isError").asBoolean()).as(response.toString()).isFalse();
        return json.readTree(response.path("content").path(0).path("text").asString());
    }

    private JsonNode call(String method, String params) throws Exception {
        var body = mvc.perform(rpc(method, params).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var response = json.readTree(body);
        assertThat(response.has("error")).as(body).isFalse();
        return response.path("result");
    }

    private static MockHttpServletRequestBuilder rpc(String method, String params) {
        return post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content("{\"jsonrpc\": \"2.0\", \"id\": 1, \"method\": \"%s\", \"params\": %s}".formatted(method, params));
    }
}
