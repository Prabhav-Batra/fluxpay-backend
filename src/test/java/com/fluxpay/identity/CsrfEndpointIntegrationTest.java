package com.fluxpay.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.identity.service.UserService;
import com.fluxpay.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Exercises the real cookie-based CSRF flow the frontend uses. Runs in a fresh context because
 * spring-security-test's csrf() post-processor permanently replaces the token repository in a shared one.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CsrfEndpointIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_issue_token_in_body_and_cookie_and_accept_it_on_login() throws Exception {
        UUID merchantId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO merchants (id, business_name, slug, platform_fee_bps, status, created_at, updated_at)"
                        + " VALUES (?, 'Jextter', 'jextter', 500, 'ACTIVE', ?, ?)",
                merchantId,
                now,
                now);
        userService.createMerchantOwner("owner@jextter.com", "correct-horse", merchantId);

        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header_name").value("X-XSRF-TOKEN"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();
        String token = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(new Cookie("XSRF-TOKEN", token))
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@jextter.com\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isOk());
    }
}
