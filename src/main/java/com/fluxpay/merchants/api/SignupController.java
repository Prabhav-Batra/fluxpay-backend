package com.fluxpay.merchants.api;

import com.fluxpay.identity.api.MeResponse;
import com.fluxpay.identity.api.SessionAuthenticator;
import com.fluxpay.identity.service.DashboardPrincipal;
import com.fluxpay.merchants.service.MerchantOnboardingService;
import com.fluxpay.merchants.service.SignupCommand;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SignupController {

    private final MerchantOnboardingService onboarding;
    private final SessionAuthenticator sessionAuthenticator;

    public SignupController(MerchantOnboardingService onboarding, SessionAuthenticator sessionAuthenticator) {
        this.onboarding = onboarding;
        this.sessionAuthenticator = sessionAuthenticator;
    }

    @PostMapping("/api/v1/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse signUp(
            @Valid @RequestBody SignupRequest body, HttpServletRequest request, HttpServletResponse response) {
        DashboardPrincipal principal =
                onboarding.signUp(new SignupCommand(body.businessName(), body.email(), body.password()));
        sessionAuthenticator.signIn(principal, request, response);
        return MeResponse.from(principal);
    }
}
