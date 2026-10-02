package com.fluxpay.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.identity.service.UserService;
import com.fluxpay.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String LOGIN_BODY = "{\"email\":\"Owner@Jextter.com\",\"password\":\"correct-horse\"}";

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createOwner() {
        UUID merchantId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO merchants (id, business_name, slug, platform_fee_bps, status, created_at, updated_at)"
                        + " VALUES (?, 'Jextter', 'jextter', 500, 'ACTIVE', ?, ?)",
                merchantId,
                now,
                now);
        userService.createMerchantOwner("owner@jextter.com", userService.hashPassword("correct-horse"), merchantId);
    }

    private Cookie login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        return session;
    }

    @Test
    void should_return_me_with_prefixed_ids_when_logged_in() throws Exception {
        Cookie session = login();

        mockMvc.perform(get("/api/v1/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner@jextter.com"))
                .andExpect(jsonPath("$.role").value("merchant_owner"))
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.startsWith("user_")))
                .andExpect(jsonPath("$.merchant_id").value(org.hamcrest.Matchers.startsWith("acct_")));
    }

    @Test
    void should_return_401_envelope_when_me_is_called_without_session() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void should_return_401_when_password_is_wrong() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@jextter.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void should_return_403_when_login_is_posted_without_csrf_token() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_TOKEN_INVALID"));
    }

    @Test
    void should_invalidate_session_when_logged_out() throws Exception {
        Cookie session = login();

        mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void should_return_401_when_unknown_api_path_is_called_anonymously() throws Exception {
        mockMvc.perform(get("/api/v1/anything")).andExpect(status().isUnauthorized());
    }
}
