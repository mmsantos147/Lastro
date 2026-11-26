package io.matheusms.lastro;

import static io.matheusms.lastro.ApiTestSupport.unique;
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

/** Manual entry through the REST API against the real database: catalog, trades, positions and errors. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ManualEntryIT {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private UserDetailsService userDetailsService;

    private final JsonMapper json = JsonMapper.builder().build();
    private ApiTestSupport api;
    private long brokerId;
    private long walletId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestSupport(users, userDetailsService);
        brokerId = create("/api/institutions", """
                {"name": "Exchange", "type": "EXCHANGE"}""");
        walletId = create("/api/institutions", """
                {"name": "Wallet", "type": "WALLET"}""");
    }

    @Test
    void shouldDerivePositionFromManualTrades() throws Exception {
        var code = unique("BBAS");
        var assetId = create("/api/assets", """
                {"code": "%s", "name": "Banco do Brasil", "assetClass": "STOCK", "quoteCurrency": "BRL"}"""
                .formatted(code));
        create("/api/trades", trade(assetId, "BUY", "2026-01-05", "100", "10.00", "5.00"));
        create("/api/trades", trade(assetId, "SELL", "2026-01-06", "40", "20.00", "3.00"));

        mvc.perform(api.getJson("/api/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value(code))
                .andExpect(jsonPath("$[0].quantity").value(60))
                .andExpect(jsonPath("$[0].averageCost").value(10.05))
                .andExpect(jsonPath("$[0].totalCost").value(603.00));
    }

    @Test
    void shouldRejectSellLargerThanThePositionWith422() throws Exception {
        var assetId = stock();
        create("/api/trades", trade(assetId, "BUY", "2026-01-05", "10", "10.00", "0"));

        mvc.perform(api.postJson("/api/trades", trade(assetId, "SELL", "2026-01-06", "11", "10.00", "0")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void shouldRejectInvalidRequestsWith400() throws Exception {
        mvc.perform(api.postJson("/api/trades", "{\"assetId\": 1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(api.postJson("/api/institutions", """
                {"name": "Broker", "type": "BROKER", "cnpj": "11.222.333/0001-00"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectDuplicatedAssetCodeWith409() throws Exception {
        var code = unique("DUP");
        var body = """
                {"code": "%s", "name": "Duplicated", "assetClass": "ETF", "quoteCurrency": "BRL"}""".formatted(code);
        create("/api/assets", body);

        mvc.perform(api.postJson("/api/assets", body)).andExpect(status().isConflict());
    }

    @Test
    void shouldKeepPositionWhenTransferringAndDeleteEntries() throws Exception {
        var assetId = stock();
        create("/api/trades", trade(assetId, "BUY", "2026-01-05", "10", "10.00", "0"));
        var transferId = create("/api/transfers", """
                {"assetId": %d, "fromInstitutionId": %d, "toInstitutionId": %d, "date": "2026-01-06",
                 "quantity": 10, "fee": 1}""".formatted(assetId, brokerId, walletId));

        mvc.perform(api.getJson("/api/positions"))
                .andExpect(jsonPath("$[0].quantity").value(9))
                .andExpect(jsonPath("$[0].averageCost").value(10.00));

        mvc.perform(api.deleteJson("/api/transfers/{id}", transferId)).andExpect(status().isNoContent());
        mvc.perform(api.getJson("/api/transfers")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void shouldRecordDistributionsWithDerivedNetAmount() throws Exception {
        var assetId = stock();
        create("/api/distributions", """
                {"assetId": %d, "institutionId": %d, "type": "JCP", "recordDate": "2026-02-01",
                 "paymentDate": "2026-03-01", "baseQuantity": 100, "currency": "BRL",
                 "grossAmount": 50.00, "withheldTax": 7.50}""".formatted(assetId, brokerId));

        mvc.perform(api.getJson("/api/distributions"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].netAmount").value(42.50));
    }

    @Test
    void shouldApplyCorporateActionsToPositions() throws Exception {
        var assetId = stock();
        create("/api/trades", trade(assetId, "BUY", "2026-01-05", "100", "10.00", "0"));
        create("/api/corporate-actions", """
                {"assetId": %d, "type": "STOCK_SPLIT", "recordDate": "2026-01-10", "factor": 2}"""
                .formatted(assetId));

        mvc.perform(api.getJson("/api/positions"))
                .andExpect(jsonPath("$[0].quantity").value(200))
                .andExpect(jsonPath("$[0].averageCost").value(5.00));
    }

    @Test
    void shouldNotShowTradesOfOtherUsers() throws Exception {
        var assetId = stock();
        create("/api/trades", trade(assetId, "BUY", "2026-01-05", "10", "10.00", "0"));
        var otherUser = new ApiTestSupport(users, userDetailsService);

        mvc.perform(otherUser.getJson("/api/trades")).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(otherUser.getJson("/api/positions")).andExpect(jsonPath("$", hasSize(0)));
    }

    private long stock() throws Exception {
        return create("/api/assets", """
                {"code": "%s", "name": "Stock", "assetClass": "STOCK", "quoteCurrency": "BRL"}"""
                .formatted(unique("STK")));
    }

    private String trade(long assetId, String type, String date, String quantity, String price, String fee) {
        return """
                {"assetId": %d, "institutionId": %d, "type": "%s", "date": "%s", "quantity": %s,
                 "unitPrice": %s, "currency": "BRL", "feeAmount": %s}"""
                .formatted(assetId, brokerId, type, date, quantity, price, fee);
    }

    private long create(String path, String body) throws Exception {
        var response = mvc.perform(api.postJson(path, body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }
}
