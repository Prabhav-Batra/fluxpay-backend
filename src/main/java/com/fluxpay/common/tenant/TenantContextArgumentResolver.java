package com.fluxpay.common.tenant;

import com.fluxpay.common.error.FluxpayException;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class TenantContextArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String MODE_HEADER = "FluxPay-Mode";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return TenantContext.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw FluxpayException.unauthenticated("UNAUTHENTICATED", "Authentication required");
        }
        if (principal.merchantId() == null) {
            throw FluxpayException.forbidden("MERCHANT_REQUIRED", "This endpoint requires a merchant account");
        }
        Mode mode = principal.fixedMode().orElseGet(() -> modeFromHeader(webRequest.getHeader(MODE_HEADER)));
        return new TenantContext(principal.merchantId(), mode);
    }

    private static Mode modeFromHeader(String header) {
        if (header == null) {
            return Mode.TEST;
        }
        return Mode.parse(header)
                .orElseThrow(() -> FluxpayException.badRequest("INVALID_MODE", MODE_HEADER + " must be test or live"));
    }
}
