package com.fluxpay.apikeys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class ApiKeyManagementIntegrationTest extends AbstractIntegrationTest {

    private static final String KEYS = "/api/v1/dashboard/api_keys";

    private SignedIn owner;

    @BeforeEach
    void signUp() throws Exception {
        owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
    }

    private MvcResult create(String mode) throws Exception {
        return mockMvc.perform(post(KEYS).with(csrf()).cookie(owner.session()).header("FluxPay-Mode", mode))
                .andExpect(status().isCreated())
                .andReturn();
    }

    @Test
    void should_return_secret_once_and_hide_it_in_list_when_key_is_created() throws Exception {
        MvcResult created = create("test");
        String json = created.getResponse().getContentAsString();

        assertThat((String) JsonPath.read(json, "$.secret")).startsWith("sk_test_");
        assertThat((String) JsonPath.read(json, "$.id")).startsWith("key_");
        mockMvc.perform(get(KEYS).cookie(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].secret").doesNotExist())
                .andExpect(jsonPath("$.data[0].display_prefix").value(org.hamcrest.Matchers.startsWith("sk_test_")));
    }

    @Test
    void should_list_only_keys_of_requested_mode() throws Exception {
        create("test");
        create("live");

        mockMvc.perform(get(KEYS).cookie(owner.session()).header("FluxPay-Mode", "live"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].mode").value("live"));
    }

    @Test
    void should_revoke_old_key_and_issue_new_one_when_rolled() throws Exception {
        String id = JsonPath.read(create("test").getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post(KEYS + "/" + id + "/roll").with(csrf()).cookie(owner.session()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.secret").value(org.hamcrest.Matchers.startsWith("sk_test_")));

        mockMvc.perform(get(KEYS).cookie(owner.session()))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(
                        jsonPath("$.data[?(@.id == '" + id + "')].revoked_at").isNotEmpty());
    }

    @Test
    void should_be_idempotent_when_revoking_twice_and_conflict_when_rolling_revoked() throws Exception {
        String id = JsonPath.read(create("test").getResponse().getContentAsString(), "$.id");

        mockMvc.perform(delete(KEYS + "/" + id).with(csrf()).cookie(owner.session()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(KEYS + "/" + id).with(csrf()).cookie(owner.session()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(KEYS + "/" + id + "/roll").with(csrf()).cookie(owner.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("API_KEY_REVOKED"));
    }

    @Test
    void should_return_404_when_key_id_is_malformed_or_belongs_to_other_mode() throws Exception {
        String liveId = JsonPath.read(create("live").getResponse().getContentAsString(), "$.id");

        mockMvc.perform(delete(KEYS + "/not-a-key").with(csrf()).cookie(owner.session()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(KEYS + "/" + liveId).with(csrf()).cookie(owner.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_return_400_when_mode_header_is_invalid() throws Exception {
        mockMvc.perform(get(KEYS).cookie(owner.session()).header("FluxPay-Mode", "prod"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_MODE"));
    }
}
