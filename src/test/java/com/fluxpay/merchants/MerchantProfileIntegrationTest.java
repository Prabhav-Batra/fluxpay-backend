package com.fluxpay.merchants;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class MerchantProfileIntegrationTest extends AbstractIntegrationTest {

    private SignedIn owner;

    @BeforeEach
    void signUp() throws Exception {
        owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
    }

    private org.springframework.test.web.servlet.ResultActions patchProfile(String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/dashboard/merchant")
                .with(csrf())
                .cookie(owner.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void should_update_only_given_fields_when_patching_profile() throws Exception {
        patchProfile("{\"logo_url\":\"https://cdn.jextter.com/logo.png\",\"brand_color\":\"#FF3366\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.business_name").value("Jextter"))
                .andExpect(jsonPath("$.logo_url").value("https://cdn.jextter.com/logo.png"))
                .andExpect(jsonPath("$.brand_color").value("#FF3366"));
    }

    @Test
    void should_clear_logo_when_empty_string_is_sent() throws Exception {
        patchProfile("{\"logo_url\":\"https://cdn.jextter.com/logo.png\"}").andExpect(status().isOk());

        patchProfile("{\"logo_url\":\"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logo_url").isEmpty());
    }

    @Test
    void should_return_422_when_logo_is_not_https_or_colour_is_invalid() throws Exception {
        patchProfile("{\"logo_url\":\"javascript:alert(1)\",\"brand_color\":\"red\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.details.length()").value(2));
    }

    @Test
    void should_return_400_when_unknown_field_is_sent() throws Exception {
        patchProfile("{\"platform_fee_bps\":0}").andExpect(status().isBadRequest());
    }
}
