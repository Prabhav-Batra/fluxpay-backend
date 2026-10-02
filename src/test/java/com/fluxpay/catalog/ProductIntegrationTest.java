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

class ProductIntegrationTest extends AbstractIntegrationTest {

    private static final String PRODUCTS = "/api/v1/dashboard/products";

    private SignedIn owner;

    @BeforeEach
    void signUp() throws Exception {
        owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
    }

    private ResultActions createRaw(String body) throws Exception {
        return mockMvc.perform(post(PRODUCTS)
                .with(csrf())
                .cookie(owner.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions patchProduct(String id, String body) throws Exception {
        return mockMvc.perform(patch(PRODUCTS + "/" + id)
                .with(csrf())
                .cookie(owner.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void should_create_product_with_defaults() throws Exception {
        createRaw("{\"name\":\"Pro Pack\",\"amount\":4900,\"metadata\":{\"coins\":\"500\"}}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.startsWith("prod_")))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.type").value("one_time"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.mode").value("test"))
                .andExpect(jsonPath("$.metadata.coins").value("500"));
    }

    @Test
    void should_page_newest_first_with_cursor() throws Exception {
        TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Starter", 1900);
        TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro", 4900);
        TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Elite", 9900);

        String first = mockMvc.perform(get(PRODUCTS + "?limit=2").cookie(owner.session()))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("Elite"))
                .andExpect(jsonPath("$.has_more").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String cursor = JsonPath.read(first, "$.cursor");

        mockMvc.perform(get(PRODUCTS + "?limit=2&starting_after=" + cursor).cookie(owner.session()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Starter"))
                .andExpect(jsonPath("$.has_more").value(false))
                .andExpect(jsonPath("$.cursor").isEmpty());
    }

    @Test
    void should_update_and_archive_and_filter_by_active() throws Exception {
        String id = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro", 4900);
        TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Elite", 9900);

        patchProduct(id, "{\"name\":\"Pro Pack\",\"amount\":5900,\"description\":\"500 coins\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pro Pack"))
                .andExpect(jsonPath("$.amount").value(5900));
        patchProduct(id, "{\"description\":\"\",\"active\":false}")
                .andExpect(jsonPath("$.description").isEmpty())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get(PRODUCTS + "?active=true").cookie(owner.session()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Elite"));
    }

    @Test
    void should_return_422_for_invalid_amount_currency_metadata_or_image() throws Exception {
        createRaw("{\"name\":\"Cheap\",\"amount\":99}").andExpect(status().isUnprocessableEntity());
        createRaw("{\"name\":\"Dollar\",\"amount\":500,\"currency\":\"USD\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.details[0].code").value("UNSUPPORTED_CURRENCY"));
        createRaw("{\"name\":\"Bad\",\"amount\":500,\"metadata\":{\"has space\":\"x\"}}")
                .andExpect(status().isUnprocessableEntity());
        createRaw("{\"name\":\"Img\",\"amount\":500,\"image_url\":\"http://x.com/a.png\"}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void should_return_400_for_bad_cursor_or_limit() throws Exception {
        mockMvc.perform(get(PRODUCTS + "?starting_after=garbage").cookie(owner.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CURSOR"));
        mockMvc.perform(get(PRODUCTS + "?limit=0").cookie(owner.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_LIMIT"));
    }

    @Test
    void should_expose_products_to_api_key_of_same_mode_only() throws Exception {
        String id = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro", 4900);
        String testKey = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
        String liveKey = TestMerchants.createApiKey(mockMvc, owner, Mode.LIVE);

        mockMvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + testKey))
                .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/api/v1/products/" + id).header("Authorization", "Bearer " + testKey))
                .andExpect(jsonPath("$.name").value("Pro"));
        mockMvc.perform(get("/api/v1/products/" + id).header("Authorization", "Bearer " + liveKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
    }
}
