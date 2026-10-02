package com.fluxpay.apikeys;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ApiKeyAuthenticationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SignedIn owner;

    @BeforeEach
    void signUp() throws Exception {
        owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
    }

    @Test
    void should_return_account_with_key_mode_when_live_key_is_used() throws Exception {
        String key = TestMerchants.createApiKey(mockMvc, owner, Mode.LIVE);

        mockMvc.perform(get("/api/v1/account").header("Authorization", "Bearer " + key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchant_id").value(owner.merchantId()))
                .andExpect(jsonPath("$.business_name").value("Jextter"))
                .andExpect(jsonPath("$.mode").value("live"));
    }

    @Test
    void should_ignore_mode_header_when_authenticated_with_api_key() throws Exception {
        String key = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);

        mockMvc.perform(get("/api/v1/account")
                        .header("Authorization", "Bearer " + key)
                        .header("FluxPay-Mode", "live"))
                .andExpect(jsonPath("$.mode").value("test"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Bearer",
                "Bearer ",
                "Basic dXNlcjpwYXNz",
                "random-string",
                "Bearer sk_test_tooShort",
                "Bearer sk_test_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            })
    void should_return_401_invalid_api_key_when_header_is_malformed_or_unknown(String header) throws Exception {
        mockMvc.perform(get("/api/v1/account").header("Authorization", header))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_API_KEY"));
    }

    @Test
    void should_return_401_when_no_credentials_are_sent() throws Exception {
        mockMvc.perform(get("/api/v1/account"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void should_return_401_when_revoked_key_is_used() throws Exception {
        String key = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
        jdbcTemplate.update("UPDATE api_keys SET revoked_at = now()");

        mockMvc.perform(get("/api/v1/account").header("Authorization", "Bearer " + key))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_API_KEY"));
    }

    @Test
    void should_return_403_when_merchant_is_suspended() throws Exception {
        String key = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
        jdbcTemplate.update("UPDATE merchants SET status = 'SUSPENDED'");

        mockMvc.perform(get("/api/v1/account").header("Authorization", "Bearer " + key))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MERCHANT_SUSPENDED"));
    }

    @Test
    void should_not_authenticate_dashboard_routes_with_api_key() throws Exception {
        String key = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);

        mockMvc.perform(get("/api/v1/dashboard/merchant").header("Authorization", "Bearer " + key))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_not_authenticate_api_routes_with_dashboard_session() throws Exception {
        mockMvc.perform(get("/api/v1/account").cookie(owner.session())).andExpect(status().isForbidden());
    }

    @Test
    void should_record_last_used_time_when_key_is_used() throws Exception {
        String key = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);

        mockMvc.perform(get("/api/v1/account").header("Authorization", "Bearer " + key));

        Integer used = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM api_keys WHERE last_used_at IS NOT NULL", Integer.class);
        org.assertj.core.api.Assertions.assertThat(used).isEqualTo(1);
    }
}
