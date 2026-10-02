package com.fluxpay.catalog;

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

class PaymentLinkIntegrationTest extends AbstractIntegrationTest {

    private static final String LINKS = "/api/v1/dashboard/payment_links";

    private SignedIn owner;
    private String productId;

    @BeforeEach
    void setUp() throws Exception {
        owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
        productId = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro Pack", 4900);
    }

    private ResultActions createLink(String body) throws Exception {
        return mockMvc.perform(post(LINKS)
                .with(csrf())
                .cookie(owner.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void should_create_link_with_frontend_url_and_slug() throws Exception {
        createLink("{\"product_id\":\"%s\",\"success_url\":\"https://jextter.com/paid\"}".formatted(productId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.startsWith("plink_")))
                .andExpect(jsonPath("$.url")
                        .value(org.hamcrest.Matchers.matchesPattern("http://localhost:3000/l/[0-9A-Za-z]{10}")))
                .andExpect(jsonPath("$.product_id").value(productId))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void should_list_and_deactivate_link() throws Exception {
        String id = JsonPath.read(
                createLink("{\"product_id\":\"%s\"}".formatted(productId))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");

        mockMvc.perform(patch(LINKS + "/" + id)
                        .with(csrf())
                        .cookie(owner.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get(LINKS).cookie(owner.session()))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void should_return_404_for_unknown_or_other_mode_product() throws Exception {
        String liveProduct = TestMerchants.createProduct(mockMvc, owner, Mode.LIVE, "Live", 4900);

        createLink("{\"product_id\":\"%s\"}".formatted(liveProduct))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
        createLink("{\"product_id\":\"prod_nope\"}").andExpect(status().isNotFound());
    }

    @Test
    void should_return_422_for_insecure_redirect() throws Exception {
        createLink("{\"product_id\":\"%s\",\"success_url\":\"http://jextter.com\"}".formatted(productId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.details[0].code").value("INSECURE_URL"));
    }
}
