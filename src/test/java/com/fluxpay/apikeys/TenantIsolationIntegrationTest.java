package com.fluxpay.apikeys;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/**
 * Merchant A must never see or change merchant B's data. Every plan adds its endpoints here.
 */
class TenantIsolationIntegrationTest extends AbstractIntegrationTest {

    private SignedIn merchantA;
    private SignedIn merchantB;
    private String keyIdOfB;

    @BeforeEach
    void setUp() throws Exception {
        merchantA = TestMerchants.signUp(mockMvc, "Alpha", "a@alpha.com");
        merchantB = TestMerchants.signUp(mockMvc, "Beta", "b@beta.com");
        String created = mockMvc.perform(
                        post("/api/v1/dashboard/api_keys").with(csrf()).cookie(merchantB.session()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        keyIdOfB = JsonPath.read(created, "$.id");
    }

    @Test
    void should_not_list_other_merchants_keys() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/api_keys").cookie(merchantA.session()))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void should_return_404_when_revoking_other_merchants_key() throws Exception {
        mockMvc.perform(delete("/api/v1/dashboard/api_keys/" + keyIdOfB)
                        .with(csrf())
                        .cookie(merchantA.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_return_404_when_rolling_other_merchants_key() throws Exception {
        mockMvc.perform(post("/api/v1/dashboard/api_keys/" + keyIdOfB + "/roll")
                        .with(csrf())
                        .cookie(merchantA.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_return_own_merchant_profile_only() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/merchant").cookie(merchantA.session()))
                .andExpect(jsonPath("$.id").value(merchantA.merchantId()));
    }

    @Test
    void should_resolve_account_to_key_owner() throws Exception {
        String keyOfA = TestMerchants.createApiKey(mockMvc, merchantA, Mode.TEST);

        mockMvc.perform(get("/api/v1/account").header("Authorization", "Bearer " + keyOfA))
                .andExpect(jsonPath("$.merchant_id").value(merchantA.merchantId()));
    }
}
