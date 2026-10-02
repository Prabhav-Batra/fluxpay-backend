package com.fluxpay.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.identity.service.UserService;
import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class AdminIntegrationTest extends AbstractIntegrationTest {

    private static final String MERCHANTS = "/api/v1/admin/merchants";

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Cookie admin;
    private SignedIn jextter;
    private String apiKey;
    private String product;

    @BeforeEach
    void setUp() throws Exception {
        userService.ensurePlatformAdmin("admin@fluxpay.in", "admin-password-1");
        admin = mockMvc.perform(TestMerchants.jsonPost(
                        "/api/v1/auth/login", "{\"email\":\"admin@fluxpay.in\",\"password\":\"admin-password-1\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie("SESSION");
        jextter = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
        apiKey = TestMerchants.createApiKey(mockMvc, jextter, Mode.TEST);
        product = TestMerchants.createProduct(mockMvc, jextter, Mode.TEST, "Pro Pack", 4900);
        TestMerchants.completeSale(mockMvc, apiKey, product, 4900, "u_1", "pay_1");
    }

    private ResultActions adminPatch(String body) throws Exception {
        return mockMvc.perform(patch(MERCHANTS + "/" + jextter.merchantId())
                .with(csrf())
                .cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions payout(String body) throws Exception {
        return mockMvc.perform(post(MERCHANTS + "/" + jextter.merchantId() + "/payouts")
                .with(csrf())
                .cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void should_list_merchants_and_show_balances_per_mode() throws Exception {
        TestMerchants.signUp(mockMvc, "Beta", "b@beta.com");

        mockMvc.perform(get(MERCHANTS).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].business_name").value("Jextter"))
                .andExpect(jsonPath("$.data[1].platform_fee_bps").value(500));
        mockMvc.perform(get(MERCHANTS + "/" + jextter.merchantId()).cookie(admin))
                .andExpect(jsonPath("$.merchant.id").value(jextter.merchantId()))
                .andExpect(jsonPath("$.balances.test.available").value(4539))
                .andExpect(jsonPath("$.balances.live.available").value(0));
    }

    @Test
    void should_apply_new_fee_to_later_sales() throws Exception {
        adminPatch("{\"platform_fee_bps\":1000}")
                .andExpect(jsonPath("$.platform_fee_bps").value(1000));

        TestMerchants.completeSale(mockMvc, apiKey, product, 4900, "u_2", "pay_2");

        assertThat(jdbcTemplate.queryForList(
                        "SELECT amount FROM ledger_entries WHERE type = 'PLATFORM_FEE' ORDER BY id", Long.class))
                .containsExactly(-245L, -490L);
    }

    @Test
    void should_block_api_keys_while_suspended() throws Exception {
        adminPatch("{\"status\":\"suspended\"}").andExpect(jsonPath("$.status").value("suspended"));
        mockMvc.perform(get("/api/v1/account").header("Authorization", "Bearer " + apiKey))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MERCHANT_SUSPENDED"));

        adminPatch("{\"status\":\"active\"}");
        mockMvc.perform(get("/api/v1/account").header("Authorization", "Bearer " + apiKey))
                .andExpect(status().isOk());
    }

    @Test
    void should_record_payout_against_balance_and_reject_overdraw() throws Exception {
        payout("{\"mode\":\"test\",\"amount\":4000,\"reference\":\"UTR123\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.startsWith("po_")))
                .andExpect(jsonPath("$.amount").value(4000));

        mockMvc.perform(get("/api/v1/dashboard/balance").cookie(jextter.session()))
                .andExpect(jsonPath("$.payouts").value(-4000))
                .andExpect(jsonPath("$.available").value(539));
        mockMvc.perform(get(MERCHANTS + "/" + jextter.merchantId() + "/payouts?mode=test")
                        .cookie(admin))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].reference").value("UTR123"));
        payout("{\"mode\":\"test\",\"amount\":1000,\"reference\":\"UTR124\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_BALANCE"));
    }

    @Test
    void should_validate_admin_input() throws Exception {
        adminPatch("{\"platform_fee_bps\":20000}").andExpect(status().isUnprocessableEntity());
        adminPatch("{\"status\":\"deleted\"}").andExpect(status().isUnprocessableEntity());
        payout("{\"mode\":\"test\",\"amount\":0,\"reference\":\"x\"}").andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get(MERCHANTS + "/acct_nope").cookie(admin)).andExpect(status().isNotFound());
    }

    @Test
    void should_forbid_merchant_owners() throws Exception {
        mockMvc.perform(get(MERCHANTS).cookie(jextter.session())).andExpect(status().isForbidden());
    }
}
