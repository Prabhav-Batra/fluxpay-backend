package com.fluxpay.analytics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import com.fluxpay.support.TestWebhooks;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class AnalyticsIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/dashboard/analytics";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SignedIn owner;
    private String proPack;
    private String elitePack;
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    @BeforeEach
    void setUp() throws Exception {
        owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
        String key = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
        proPack = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro Pack", 4900);
        elitePack = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Elite Pack", 9900);
        TestMerchants.completeSale(mockMvc, key, proPack, 4900, "u_1", "pay_1");
        TestMerchants.completeSale(mockMvc, key, elitePack, 9900, "u_2", "pay_2");
        TestMerchants.completeSale(mockMvc, key, proPack, 4900, "u_3", "pay_3");
        String refund = TestWebhooks.refundProcessed("rfnd_1", "pay_3", 2000);
        mockMvc.perform(post("/api/v1/gateway-webhooks/razorpay/test")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Razorpay-Signature", TestWebhooks.sign(refund))
                .content(refund));
    }

    private ResultActions call(String path) throws Exception {
        return mockMvc.perform(get(BASE + path).cookie(owner.session()));
    }

    @Test
    void should_summarise_gross_refunds_fees_net_and_count() throws Exception {
        call("/summary")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gross_sales").value(19700))
                .andExpect(jsonPath("$.refunds").value(2000))
                .andExpect(jsonPath("$.platform_fees").value(985))
                .andExpect(jsonPath("$.gateway_fees").value(348))
                .andExpect(jsonPath("$.net").value(16367))
                .andExpect(jsonPath("$.sales_count").value(3))
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    void should_bucket_sales_per_local_day_with_empty_days_filled() throws Exception {
        jdbcTemplate.update(
                "UPDATE sales SET created_at = created_at - interval '2 days'" + " WHERE customer_ref = 'u_2'");

        call("/timeseries?from=" + today.minusDays(3) + "&to=" + today)
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].date").value(today.minusDays(3).toString()))
                .andExpect(jsonPath("$.data[0].sales_count").value(0))
                .andExpect(jsonPath("$.data[1].revenue").value(9900))
                .andExpect(jsonPath("$.data[3].sales_count").value(2))
                .andExpect(jsonPath("$.data[3].revenue").value(9800));
    }

    @Test
    void should_rank_top_products_by_revenue() throws Exception {
        call("/top_products?limit=5")
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].product_id").value(elitePack))
                .andExpect(jsonPath("$.data[0].units").value(1))
                .andExpect(jsonPath("$.data[1].product_id").value(proPack))
                .andExpect(jsonPath("$.data[1].units").value(2))
                .andExpect(jsonPath("$.data[1].revenue").value(9800));
    }

    @Test
    void should_reject_invalid_ranges() throws Exception {
        call("/summary?from=" + today + "&to=" + today.minusDays(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_RANGE"));
        call("/summary?from=" + today.minusDays(400) + "&to=" + today)
                .andExpect(jsonPath("$.error.code").value("INVALID_RANGE"));
        call("/summary?from=yesterday").andExpect(status().isBadRequest());
        call("/top_products?limit=0").andExpect(status().isBadRequest());
    }

    @Test
    void should_isolate_by_mode_and_merchant() throws Exception {
        mockMvc.perform(get(BASE + "/summary").cookie(owner.session()).header("FluxPay-Mode", "live"))
                .andExpect(jsonPath("$.gross_sales").value(0))
                .andExpect(jsonPath("$.sales_count").value(0));
        SignedIn other = TestMerchants.signUp(mockMvc, "Beta", "b@beta.com");
        mockMvc.perform(get(BASE + "/top_products").cookie(other.session()))
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
