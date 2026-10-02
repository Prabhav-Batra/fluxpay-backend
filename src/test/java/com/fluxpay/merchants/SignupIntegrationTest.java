package com.fluxpay.merchants;

import static com.fluxpay.support.TestMerchants.jsonPost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class SignupIntegrationTest extends AbstractIntegrationTest {

    private static final String BODY =
            "{\"business_name\":\"Jextter\",\"email\":\"owner@jextter.com\",\"password\":\"correct-horse\"}";

    @Test
    void should_create_merchant_owner_and_session_when_signing_up() throws Exception {
        MvcResult result = mockMvc.perform(jsonPost("/api/v1/auth/signup", BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("merchant_owner"))
                .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");

        mockMvc.perform(get("/api/v1/dashboard/merchant").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.business_name").value("Jextter"))
                .andExpect(jsonPath("$.slug").value("jextter"))
                .andExpect(jsonPath("$.platform_fee_bps").value(500))
                .andExpect(jsonPath("$.status").value("active"));
    }

    @Test
    void should_give_unique_slug_when_two_merchants_share_a_name() throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/signup", BODY)).andExpect(status().isCreated());
        MvcResult second = mockMvc.perform(
                        jsonPost(
                                "/api/v1/auth/signup",
                                "{\"business_name\":\"Jextter\",\"email\":\"other@jextter.com\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(get("/api/v1/dashboard/merchant")
                        .cookie(second.getResponse().getCookie("SESSION")))
                .andExpect(jsonPath("$.slug").value(org.hamcrest.Matchers.matchesPattern("jextter-[a-z0-9]{6}")));
    }

    @Test
    void should_return_409_when_email_is_taken_in_different_case() throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/signup", BODY)).andExpect(status().isCreated());

        mockMvc.perform(jsonPost("/api/v1/auth/signup", BODY.replace("owner@jextter.com", " OWNER@Jextter.com ")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_TAKEN"));
    }

    @Test
    void should_return_one_201_and_one_409_when_same_email_signs_up_concurrently() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Integer> signup = () -> mockMvc.perform(jsonPost("/api/v1/auth/signup", BODY))
                .andReturn()
                .getResponse()
                .getStatus();
        List<Future<Integer>> futures = pool.invokeAll(List.of(signup, signup));
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get());
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    }

    @Test
    void should_return_422_when_password_is_too_long_in_bytes() throws Exception {
        String body = BODY.replace("correct-horse", "क".repeat(30));

        mockMvc.perform(jsonPost("/api/v1/auth/signup", body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.details[0].code").value("PASSWORD_TOO_LONG"));
    }

    @Test
    void should_return_422_when_email_is_invalid_or_business_name_blank() throws Exception {
        mockMvc.perform(jsonPost(
                        "/api/v1/auth/signup",
                        "{\"business_name\":\"\",\"email\":\"not-an-email\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.details.length()").value(2));
    }

    @Test
    void should_create_valid_slug_when_business_name_is_non_latin() throws Exception {
        MvcResult result = mockMvc.perform(jsonPost("/api/v1/auth/signup", BODY.replace("Jextter", "जेक्सटर")))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(get("/api/v1/dashboard/merchant")
                        .cookie(result.getResponse().getCookie("SESSION")))
                .andExpect(jsonPath("$.slug").value("merchant"))
                .andExpect(jsonPath("$.business_name").value("जेक्सटर"));
    }
}
