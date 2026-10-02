package com.fluxpay.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.common.tenant.Mode;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Request builders and fixtures shared by integration tests. */
public final class TestMerchants {

    public static final String PASSWORD = "correct-horse";

    public record SignedIn(Cookie session, String merchantId, String userId) {}

    private TestMerchants() {}

    public static MockHttpServletRequestBuilder jsonPost(String url, String body) {
        return MockMvcRequestBuilders.post(url)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    public static SignedIn signUp(MockMvc mockMvc, String businessName, String email) throws Exception {
        String body = "{\"business_name\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}"
                .formatted(businessName, email, PASSWORD);
        MvcResult result = mockMvc.perform(jsonPost("/api/v1/auth/signup", body))
                .andExpect(status().isCreated())
                .andReturn();
        String json = result.getResponse().getContentAsString();
        return new SignedIn(
                result.getResponse().getCookie("SESSION"),
                JsonPath.read(json, "$.merchant_id"),
                JsonPath.read(json, "$.id"));
    }

    public static String createApiKey(MockMvc mockMvc, SignedIn merchant, Mode mode) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/dashboard/api_keys")
                        .with(csrf())
                        .cookie(merchant.session())
                        .header("FluxPay-Mode", mode.value()))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.secret");
    }

    public static String createProduct(MockMvc mockMvc, SignedIn merchant, Mode mode, String name, long amount)
            throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/dashboard/products")
                        .with(csrf())
                        .cookie(merchant.session())
                        .header("FluxPay-Mode", mode.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"amount\":%d}".formatted(name, amount)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
