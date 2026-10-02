package com.fluxpay.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class CheckoutSessionIntegrationTest extends AbstractIntegrationTest {

    private SignedIn owner;
    private String productId;
    private String testKey;

    @BeforeEach
    void setUp() throws Exception {
        owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
        productId = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro Pack", 4900);
        testKey = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
    }

    private ResultActions create(String key, String idempotencyKey, String body) throws Exception {
        var request = post("/api/v1/checkout_sessions")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    private String body(String product, String successUrl) {
        return """
                {"product_id":"%s","customer_ref":"u_123","success_url":"%s","cancel_url":"https://jextter.com/store"}"""
                .formatted(product, successUrl);
    }

    @Test
    void should_create_open_session_with_price_snapshot_and_hosted_url() throws Exception {
        create(testKey, null, body(productId, "https://jextter.com/paid"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.startsWith("cs_")))
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("http://localhost:3000/pay/cs_")))
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.amount").value(4900))
                .andExpect(jsonPath("$.customer_ref").value("u_123"))
                .andExpect(jsonPath("$.mode").value("test"));
    }

    @Test
    void should_keep_snapshot_price_when_product_is_repriced() throws Exception {
        String id = TestMerchants.createCheckoutSession(mockMvc, testKey, productId, "u_123");
        mockMvc.perform(patch("/api/v1/dashboard/products/" + productId)
                .with(csrf())
                .cookie(owner.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":9900}"));

        mockMvc.perform(get("/api/v1/checkout_sessions/" + id).header("Authorization", "Bearer " + testKey))
                .andExpect(jsonPath("$.amount").value(4900));
    }

    @Test
    void should_reject_archived_unknown_and_other_mode_products() throws Exception {
        String liveKey = TestMerchants.createApiKey(mockMvc, owner, Mode.LIVE);
        create(liveKey, null, body(productId, "https://jextter.com/paid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
        create(testKey, null, body("prod_nope", "https://jextter.com/paid")).andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/dashboard/products/" + productId)
                .with(csrf())
                .cookie(owner.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"));
        create(testKey, null, body(productId, "https://jextter.com/paid"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_INACTIVE"));
    }

    @Test
    void should_enforce_redirect_policy_per_mode() throws Exception {
        create(testKey, null, body(productId, "http://localhost:3000/paid")).andExpect(status().isCreated());
        create(testKey, null, body(productId, "http://jextter.com/paid"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.details[0].code").value("INSECURE_URL"));
        create(testKey, null, body(productId, "javascript:alert(1)")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void should_replay_same_idempotency_key_and_reject_different_body() throws Exception {
        String first = create(testKey, "order-42", body(productId, "https://jextter.com/paid"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String replay = create(testKey, "order-42", body(productId, "https://jextter.com/paid"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat((String) JsonPath.read(replay, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
        create(testKey, "order-42", body(productId, "https://jextter.com/other"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void should_return_404_for_session_of_other_mode() throws Exception {
        String id = TestMerchants.createCheckoutSession(mockMvc, testKey, productId, "u_123");
        String liveKey = TestMerchants.createApiKey(mockMvc, owner, Mode.LIVE);

        mockMvc.perform(get("/api/v1/checkout_sessions/" + id).header("Authorization", "Bearer " + liveKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHECKOUT_SESSION_NOT_FOUND"));
    }
}
