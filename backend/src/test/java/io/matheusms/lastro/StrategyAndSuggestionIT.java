package io.matheusms.lastro;

import static io.matheusms.lastro.ApiTestSupport.unique;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.matheusms.lastro.domain.port.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Strategy CRUD, allocation vs target and the persisted contribution suggestion, through the REST API. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StrategyAndSuggestionIT {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private UserDetailsService userDetailsService;

    private final JsonMapper json = JsonMapper.builder().build();
    private ApiTestSupport api;
    private long brokerId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestSupport(users, userDetailsService);
        brokerId = create("/api/institutions", "{\"name\": \"Exchange\", \"type\": \"EXCHANGE\"}");
    }

    @Test
    void shouldWarnThatThereIsNoTargetWithoutAnActiveStrategy() throws Exception {
        mvc.perform(api.postJson("/api/contribution-suggestions", "{\"amount\": 1000}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NO_ACTIVE_STRATEGY"));
        mvc.perform(api.getJson("/api/allocation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").doesNotExist());
    }

    @Test
    void shouldRejectTargetsThatDoNotAddUpToOneHundred() throws Exception {
        mvc.perform(api.postJson("/api/strategies", """
                        {"name": "Broken", "active": true, "targets": [
                          {"assetClass": "STOCK", "targetPercentage": 60},
                          {"assetClass": "FII", "targetPercentage": 30}]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldSuggestAndPersistTheContributionFollowingTheActiveStrategy() throws Exception {
        var stock = asset("STOCK");
        var fii = asset("FII");
        buy(stock, "60", "10.00");
        buy(fii, "40", "10.00");
        create("/api/strategies", """
                {"name": "Renda e crescimento", "active": true, "targets": [
                  {"assetClass": "STOCK", "targetPercentage": 50, "minTolerance": 5, "maxTolerance": 5},
                  {"assetClass": "FII", "targetPercentage": 50, "minTolerance": 5, "maxTolerance": 5}]}""");

        mvc.perform(api.getJson("/api/allocation"))
                .andExpect(jsonPath("$.strategy.name").value("Renda e crescimento"))
                .andExpect(jsonPath("$.buckets[0].status").value("ABOVE"))
                .andExpect(jsonPath("$.buckets[1].status").value("BELOW"));

        var id = create("/api/contribution-suggestions", "{\"amount\": 200}");

        mvc.perform(api.getJson("/api/contribution-suggestions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(closeTo(200.0, 0.001)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].assetId").value(fii))
                .andExpect(jsonPath("$.items[0].reason").value("BELOW_TARGET"))
                .andExpect(jsonPath("$.items[0].suggestedAmount").value(closeTo(200.0, 0.001)));
    }

    @Test
    void shouldKeepASingleActiveStrategy() throws Exception {
        var first = create("/api/strategies", """
                {"name": "A", "active": true, "targets": [{"assetClass": "STOCK", "targetPercentage": 100}]}""");
        var second = create("/api/strategies", """
                {"name": "B", "active": false, "targets": [{"assetClass": "FII", "targetPercentage": 100}]}""");

        mvc.perform(api.postJson("/api/strategies/" + second + "/activate", "")).andExpect(status().isOk());

        mvc.perform(api.getJson("/api/strategies"))
                .andExpect(jsonPath("$[?(@.id == %d)].active".formatted(first)).value(false))
                .andExpect(jsonPath("$[?(@.id == %d)].active".formatted(second)).value(true));
    }

    @Test
    void shouldNotShowSuggestionsOfOtherUsers() throws Exception {
        create("/api/strategies", """
                {"name": "A", "active": true, "targets": [{"assetClass": "STOCK", "targetPercentage": 100}]}""");
        var id = create("/api/contribution-suggestions", "{\"amount\": 100}");

        mvc.perform(new ApiTestSupport(users, userDetailsService).getJson("/api/contribution-suggestions/{id}", id))
                .andExpect(status().isNotFound());
    }

    private long asset(String assetClass) throws Exception {
        return create("/api/assets", """
                {"code": "%s", "name": "Asset", "assetClass": "%s", "quoteCurrency": "BRL"}"""
                .formatted(unique(assetClass.substring(0, 2)), assetClass));
    }

    private void buy(long assetId, String quantity, String price) throws Exception {
        create("/api/trades", """
                {"assetId": %d, "institutionId": %d, "type": "BUY", "date": "2026-01-05", "quantity": %s,
                 "unitPrice": %s, "currency": "BRL", "feeAmount": 0}""".formatted(assetId, brokerId, quantity, price));
    }

    private long create(String path, String body) throws Exception {
        var response = mvc.perform(api.postJson(path, body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }
}
