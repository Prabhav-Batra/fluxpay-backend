package com.fluxpay.events.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** {@code FluxPay-Signature: t=<unix>,v1=<hex HMAC-SHA256(secret, t + "." + body)>} (spec §7). */
public final class WebhookSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private WebhookSigner() {}

    public static String header(String secret, byte[] body, long epochSeconds) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            mac.update((epochSeconds + ".").getBytes(StandardCharsets.UTF_8));
            return "t=" + epochSeconds + ",v1=" + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
