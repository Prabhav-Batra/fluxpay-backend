package com.fluxpay.common.web;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.tenant.Mode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Where FluxPay may send a customer after checkout. Prevents open redirects and javascript: URLs. */
public final class RedirectUrlPolicy {

    static final int MAX_LENGTH = 2048;

    private RedirectUrlPolicy() {}

    public static void validate(Mode mode, String field, String url) {
        if (url == null) {
            return;
        }
        if (url.length() > MAX_LENGTH) {
            throw FluxpayException.validation(field, "URL_TOO_LONG", field + " must be at most 2048 characters");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw invalid(field);
        }
        if (uri.getScheme() == null || uri.getHost() == null || uri.getUserInfo() != null) {
            throw invalid(field);
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean localHttp =
                "http".equals(scheme) && mode == Mode.TEST && ("localhost".equals(host) || "127.0.0.1".equals(host));
        if (!"https".equals(scheme) && !localHttp) {
            throw FluxpayException.validation(
                    field, "INSECURE_URL", field + " must use https (http is allowed only for localhost in test mode)");
        }
    }

    private static FluxpayException invalid(String field) {
        return FluxpayException.validation(field, "INVALID_URL", field + " must be an absolute http(s) URL");
    }
}
