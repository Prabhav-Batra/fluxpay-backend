package com.fluxpay.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class WebhookEndpointIntegrationTest extends AbstractIntegrationTest {

    private static final String ENDPOINTS = "/api/v1/dashboard/webhook_endpoints";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SignedIn owner;

    @BeforeEach
    void signUp() throws Exception {
        owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
    }

    private ResultActions create(String mode, String url) throws Exception {
        return mockMvc.perform(post(ENDPOINTS)
                .with(csrf())
                .cookie(owner.session())
                .header("FluxPay-Mode", mode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"%s\"}".formatted(url)));
    }

    private String createId(String url) throws Exception {
        return JsonPath.read(create("test", url).andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void should_return_secret_on_create_and_hide_it_in_list() throws Exception {
        create("test", "http://localhost:9999/hooks")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.startsWith("we_")))
                .andExpect(jsonPath("$.secret").value(org.hamcrest.Matchers.startsWith("whsec_")))
                .andExpect(jsonPath("$.enabled").value(true));

        mockMvc.perform(get(ENDPOINTS).cookie(owner.session()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].secret").doesNotExist());
    }

    @Test
    void should_block_internal_and_insecure_urls_in_live_mode() throws Exception {
        create("live", "https://localhost/hooks")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.details[0].code").value("PRIVATE_ADDRESS"));
        create("live", "https://10.1.2.3/hooks").andExpect(status().isUnprocessableEntity());
        create("live", "https://169.254.169.254/latest").andExpect(status().isUnprocessableEntity());
        create("live", "http://93.184.216.34/hooks")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.details[0].code").value("INSECURE_URL"));
        create("live", "https://93.184.216.34/hooks").andExpect(status().isCreated());
        create("test", "http://10.1.2.3/hooks").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void should_limit_endpoints_per_mode() throws Exception {
        for (int i = 0; i < 5; i++) {
            create("test", "http://localhost:9999/h" + i).andExpect(status().isCreated());
        }

        create("test", "http://localhost:9999/h6")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.details[0].code").value("ENDPOINT_LIMIT_REACHED"));
    }

    @Test
    void should_update_roll_and_delete_endpoint() throws Exception {
        String id = createId("http://localhost:9999/hooks");

        mockMvc.perform(patch(ENDPOINTS + "/" + id)
                        .with(csrf())
                        .cookie(owner.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"url\":\"http://localhost:9999/v2\"}"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.url").value("http://localhost:9999/v2"));
        String rolled = mockMvc.perform(
                        post(ENDPOINTS + "/" + id + "/roll_secret").with(csrf()).cookie(owner.session()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat((String) JsonPath.read(rolled, "$.secret")).startsWith("whsec_");

        mockMvc.perform(delete(ENDPOINTS + "/" + id).with(csrf()).cookie(owner.session()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(ENDPOINTS).cookie(owner.session()))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void should_fan_out_events_to_enabled_endpoints_of_same_mode_only() throws Exception {
        createId("http://localhost:9999/a");
        String disabled = createId("http://localhost:9999/b");
        mockMvc.perform(patch(ENDPOINTS + "/" + disabled)
                .with(csrf())
                .cookie(owner.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"));
        create("live", "https://93.184.216.34/hooks");
        String key = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
        String product = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro Pack", 4900);

        TestMerchants.completeSale(mockMvc, key, product, 4900, "u_1", "pay_1");

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM webhook_deliveries WHERE status = 'PENDING'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void should_list_event_with_deliveries_resend_and_ping() throws Exception {
        String endpoint = createId("http://localhost:9999/a");
        String key = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
        String product = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro Pack", 4900);
        TestMerchants.completeSale(mockMvc, key, product, 4900, "u_1", "pay_1");

        String events = mockMvc.perform(get("/api/v1/dashboard/events").cookie(owner.session()))
                .andExpect(jsonPath("$.data[0].type").value("checkout.completed"))
                .andExpect(jsonPath("$.data[0].data.customer_ref").value("u_1"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String eventId = JsonPath.read(events, "$.data[0].id");

        mockMvc.perform(post("/api/v1/dashboard/events/" + eventId + "/resend")
                        .with(csrf())
                        .cookie(owner.session()))
                .andExpect(status().isAccepted());
        mockMvc.perform(get("/api/v1/dashboard/events/" + eventId).cookie(owner.session()))
                .andExpect(jsonPath("$.deliveries.length()").value(2))
                .andExpect(jsonPath("$.deliveries[0].endpoint_id").value(endpoint))
                .andExpect(jsonPath("$.deliveries[0].status").value("pending"));

        mockMvc.perform(post("/api/v1/dashboard/webhook_endpoints/" + endpoint + "/test")
                        .with(csrf())
                        .cookie(owner.session()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.type").value("webhook.test"));
    }

    @Test
    void should_hide_other_merchants_endpoints_and_events() throws Exception {
        String endpointOfA = createId("http://localhost:9999/a");
        SignedIn other = TestMerchants.signUp(mockMvc, "Beta", "b@beta.com");

        mockMvc.perform(delete(ENDPOINTS + "/" + endpointOfA).with(csrf()).cookie(other.session()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(ENDPOINTS + "/" + endpointOfA + "/test")
                        .with(csrf())
                        .cookie(other.session()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(ENDPOINTS).cookie(other.session()))
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/v1/dashboard/events/evt_nope").cookie(other.session()))
                .andExpect(status().isNotFound());
    }
}
