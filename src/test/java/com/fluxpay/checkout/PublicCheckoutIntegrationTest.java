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
import com.fluxpay.support.FakePaymentGateway;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class PublicCheckoutIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SignedIn owner;
    private String productId;
    private String sessionId;

    @BeforeEach
    void setUp() throws Exception {
        owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
        productId = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro Pack", 4900);
        String key = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
        sessionId = TestMerchants.createCheckoutSession(mockMvc, key, productId, "u_123");
    }

    @Test
    void should_show_branding_and_price_without_success_url_while_open() throws Exception {
        mockMvc.perform(get("/api/v1/public/checkout_sessions/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.amount").value(4900))
                .andExpect(jsonPath("$.product.name").value("Pro Pack"))
                .andExpect(jsonPath("$.merchant.name").value("Jextter"))
                .andExpect(jsonPath("$.cancel_url").value("https://jextter.com/store"))
                .andExpect(jsonPath("$.success_url").isEmpty());
    }

    @Test
    void should_return_404_for_unknown_session() throws Exception {
        mockMvc.perform(get("/api/v1/public/checkout_sessions/cs_nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHECKOUT_SESSION_NOT_FOUND"));
    }

    @Test
    void should_create_one_gateway_order_and_reuse_it() throws Exception {
        String first = TestMerchants.pay(mockMvc, sessionId);
        String second = TestMerchants.pay(mockMvc, sessionId);

        assertThat(second).isEqualTo(first);
        assertThat(paymentGateway.createdOrders()).hasSize(1);
        mockMvc.perform(post("/api/v1/public/checkout_sessions/" + sessionId + "/pay"))
                .andExpect(jsonPath("$.key_id").value(FakePaymentGateway.PUBLIC_KEY_ID))
                .andExpect(jsonPath("$.amount").value(4900))
                .andExpect(jsonPath("$.merchant_name").value("Jextter"));
    }

    @Test
    void should_converge_on_one_attached_order_when_pay_is_double_clicked() throws Exception {
        paymentGateway.setOrderDelay(Duration.ofMillis(300));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<String> pay = () -> TestMerchants.pay(mockMvc, sessionId);

        List<Future<String>> results = pool.invokeAll(List.of(pay, pay));
        pool.shutdown();

        String attached = jdbcTemplate.queryForObject("SELECT gateway_order_id FROM checkout_sessions", String.class);
        assertThat(results.get(0).get()).isEqualTo(attached);
        assertThat(results.get(1).get()).isEqualTo(attached);
    }

    @Test
    void should_refuse_payment_for_expired_session_and_show_expired() throws Exception {
        jdbcTemplate.update("UPDATE checkout_sessions SET expires_at = now() - interval '1 minute'");

        mockMvc.perform(post("/api/v1/public/checkout_sessions/" + sessionId + "/pay"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SESSION_EXPIRED"));
        mockMvc.perform(get("/api/v1/public/checkout_sessions/" + sessionId))
                .andExpect(jsonPath("$.status").value("expired"));
    }

    @Test
    void should_return_502_when_gateway_fails() throws Exception {
        paymentGateway.failNextOrder();

        mockMvc.perform(post("/api/v1/public/checkout_sessions/" + sessionId + "/pay"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("GATEWAY_UNAVAILABLE"));
    }

    @Test
    void should_create_session_from_active_payment_link_with_ref() throws Exception {
        String link = mockMvc.perform(post("/api/v1/dashboard/payment_links")
                        .with(csrf())
                        .cookie(owner.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_id\":\"%s\"}".formatted(productId)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String slug = JsonPath.read(link, "$.slug");

        String created = mockMvc.perform(post("/api/v1/public/payment_links/" + slug + "/checkout_sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ref\":\"u_777\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("http://localhost:3000/pay/")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String linkedSession = JsonPath.read(created, "$.id");
        String apiKey = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
        mockMvc.perform(get("/api/v1/checkout_sessions/" + linkedSession).header("Authorization", "Bearer " + apiKey))
                .andExpect(jsonPath("$.customer_ref").value("u_777"))
                .andExpect(jsonPath("$.payment_link_id").value(JsonPath.<String>read(link, "$.id")));

        mockMvc.perform(patch("/api/v1/dashboard/payment_links/" + JsonPath.read(link, "$.id"))
                .with(csrf())
                .cookie(owner.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"));
        mockMvc.perform(post("/api/v1/public/payment_links/" + slug + "/checkout_sessions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_LINK_NOT_FOUND"));
    }
}
