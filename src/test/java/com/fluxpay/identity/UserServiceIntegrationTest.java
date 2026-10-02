package com.fluxpay.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.identity.domain.Role;
import com.fluxpay.identity.service.DashboardPrincipal;
import com.fluxpay.identity.service.UserService;
import com.fluxpay.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class UserServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID merchantId;

    @BeforeEach
    void insertMerchant() {
        merchantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO merchants (id, business_name, slug, platform_fee_bps, status, created_at, updated_at)"
                        + " VALUES (?, 'Jextter', ?, 500, 'ACTIVE', ?, ?)",
                merchantId,
                "jextter-" + merchantId,
                java.sql.Timestamp.from(Instant.now()),
                java.sql.Timestamp.from(Instant.now()));
    }

    @Test
    void should_normalize_email_when_creating_owner_and_authenticating() {
        DashboardPrincipal created = userService.createMerchantOwner(
                " Owner@Jextter.com ", userService.hashPassword("correct-horse"), merchantId);

        DashboardPrincipal loggedIn = userService.authenticate("owner@jextter.COM", "correct-horse");

        assertThat(created.email()).isEqualTo("owner@jextter.com");
        assertThat(loggedIn.userId()).isEqualTo(created.userId());
        assertThat(loggedIn.role()).isEqualTo(Role.MERCHANT_OWNER);
        assertThat(loggedIn.merchantId()).isEqualTo(merchantId);
    }

    @Test
    void should_throw_email_taken_when_email_differs_only_by_case() {
        userService.createMerchantOwner("owner@jextter.com", userService.hashPassword("correct-horse"), merchantId);

        assertThatThrownBy(() -> userService.createMerchantOwner(
                        "OWNER@jextter.com", userService.hashPassword("correct-horse"), merchantId))
                .isInstanceOf(FluxpayException.class)
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("EMAIL_TAKEN");
    }

    @Test
    void should_throw_email_taken_when_reserving_registered_email() {
        userService.createMerchantOwner("owner@jextter.com", userService.hashPassword("correct-horse"), merchantId);

        assertThatThrownBy(() -> userService.reserveEmail(" Owner@Jextter.com"))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("EMAIL_TAKEN");
    }

    @Test
    void should_throw_invalid_credentials_when_password_is_wrong_or_user_unknown() {
        userService.createMerchantOwner("owner@jextter.com", userService.hashPassword("correct-horse"), merchantId);

        assertThatThrownBy(() -> userService.authenticate("owner@jextter.com", "wrong-password"))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("INVALID_CREDENTIALS");
        assertThatThrownBy(() -> userService.authenticate("nobody@jextter.com", "correct-horse"))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void should_create_admin_once_when_ensure_platform_admin_is_called_twice() {
        userService.ensurePlatformAdmin("admin@fluxpay.in", "admin-password-1");
        userService.ensurePlatformAdmin("admin@fluxpay.in", "admin-password-1");

        DashboardPrincipal admin = userService.authenticate("admin@fluxpay.in", "admin-password-1");
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class);

        assertThat(admin.role()).isEqualTo(Role.PLATFORM_ADMIN);
        assertThat(admin.merchantId()).isNull();
        assertThat(count).isEqualTo(1);
    }
}
