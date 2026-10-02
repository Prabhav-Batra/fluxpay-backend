package com.fluxpay.identity.api;

import com.fluxpay.identity.service.DashboardPrincipal;
import com.fluxpay.identity.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final SessionAuthenticator sessionAuthenticator;

    public AuthController(UserService userService, SessionAuthenticator sessionAuthenticator) {
        this.userService = userService;
        this.sessionAuthenticator = sessionAuthenticator;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getToken(), token.getHeaderName());
    }

    @PostMapping("/login")
    public MeResponse login(
            @Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        DashboardPrincipal principal = userService.authenticate(body.email(), body.password());
        sessionAuthenticator.signIn(principal, request, response);
        return MeResponse.from(principal);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        sessionAuthenticator.signOut(request, response);
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal DashboardPrincipal principal) {
        return MeResponse.from(principal);
    }
}
