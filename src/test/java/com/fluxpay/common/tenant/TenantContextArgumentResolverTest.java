package com.fluxpay.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluxpay.common.error.ErrorType;
import com.fluxpay.common.error.FluxpayException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.ServletWebRequest;

class TenantContextArgumentResolverTest {

    record FakePrincipal(UUID merchantId, Optional<Mode> fixedMode) implements TenantPrincipal {}

    private final TenantContextArgumentResolver resolver = new TenantContextArgumentResolver();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(TenantPrincipal principal) {
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    private TenantContext resolve() throws Exception {
        return (TenantContext) resolver.resolveArgument(null, null, new ServletWebRequest(request), null);
    }

    @Test
    void should_default_to_test_mode_when_dashboard_principal_sends_no_header() throws Exception {
        UUID merchantId = UUID.randomUUID();
        authenticate(new FakePrincipal(merchantId, Optional.empty()));

        assertThat(resolve()).isEqualTo(new TenantContext(merchantId, Mode.TEST));
    }

    @Test
    void should_use_header_mode_when_dashboard_principal_sends_live() throws Exception {
        authenticate(new FakePrincipal(UUID.randomUUID(), Optional.empty()));
        request.addHeader(TenantContextArgumentResolver.MODE_HEADER, "live");

        assertThat(resolve().mode()).isEqualTo(Mode.LIVE);
    }

    @Test
    void should_ignore_header_when_principal_has_fixed_mode() throws Exception {
        authenticate(new FakePrincipal(UUID.randomUUID(), Optional.of(Mode.TEST)));
        request.addHeader(TenantContextArgumentResolver.MODE_HEADER, "live");

        assertThat(resolve().mode()).isEqualTo(Mode.TEST);
    }

    @Test
    void should_reject_with_bad_request_when_mode_header_is_garbage() {
        authenticate(new FakePrincipal(UUID.randomUUID(), Optional.empty()));
        request.addHeader(TenantContextArgumentResolver.MODE_HEADER, "prod");

        assertThatThrownBy(this::resolve)
                .isInstanceOf(FluxpayException.class)
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("INVALID_MODE");
    }

    @Test
    void should_reject_with_forbidden_when_principal_has_no_merchant() {
        authenticate(new FakePrincipal(null, Optional.empty()));

        assertThatThrownBy(this::resolve)
                .isInstanceOf(FluxpayException.class)
                .extracting(e -> ((FluxpayException) e).type())
                .isEqualTo(ErrorType.FORBIDDEN);
    }

    @Test
    void should_reject_with_unauthenticated_when_no_principal() {
        assertThatThrownBy(this::resolve)
                .isInstanceOf(FluxpayException.class)
                .extracting(e -> ((FluxpayException) e).type())
                .isEqualTo(ErrorType.UNAUTHENTICATED);
    }
}
