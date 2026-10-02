package com.fluxpay.common.ratelimit;

import static com.fluxpay.support.TestMerchants.jsonPost;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

// Spring replaces a whole list from the highest-priority source, so the full rule is restated here.
@TestPropertySource(
        properties = {
            "fluxpay.rate-limit.rules[0].name=auth",
            "fluxpay.rate-limit.rules[0].method=POST",
            "fluxpay.rate-limit.rules[0].paths[0]=/api/v1/auth/login",
            "fluxpay.rate-limit.rules[0].capacity=2",
            "fluxpay.rate-limit.rules[0].period=1m"
        })
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    private static final String BODY = "{\"email\":\"x@y.com\",\"password\":\"whatever-123\"}";

    @Test
    void should_return_429_with_headers_when_login_limit_is_exceeded() throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/login", BODY))
                .andExpect(header().string("X-RateLimit-Limit", "2"))
                .andExpect(header().string("X-RateLimit-Remaining", "1"));
        mockMvc.perform(jsonPost("/api/v1/auth/login", BODY));

        mockMvc.perform(jsonPost("/api/v1/auth/login", BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
    }
}
