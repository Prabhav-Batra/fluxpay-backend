package com.fluxpay.events.service;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.tenant.Mode;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Where FluxPay may POST merchant webhooks. Blocks internal addresses (SSRF): live endpoints must be public
 * https hosts; test mode additionally allows http on loopback so merchants can develop locally.
 */
public final class WebhookUrlPolicy {

    static final int MAX_LENGTH = 2048;
    private static final String FIELD = "url";

    private WebhookUrlPolicy() {}

    public static void validate(Mode mode, String url) {
        if (url == null || url.length() > MAX_LENGTH) {
            throw invalid("INVALID_URL", "url must be an absolute URL of at most 2048 characters");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw invalid("INVALID_URL", "url must be an absolute http(s) URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (uri.getHost() == null || uri.getUserInfo() != null || !(scheme.equals("https") || scheme.equals("http"))) {
            throw invalid("INVALID_URL", "url must be an absolute http(s) URL");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException e) {
            throw invalid("UNRESOLVABLE_HOST", "url host does not resolve");
        }
        boolean allLoopback = true;
        for (InetAddress address : addresses) {
            boolean loopback = address.isLoopbackAddress();
            allLoopback &= loopback;
            boolean allowedLoopback = loopback && mode == Mode.TEST;
            if (isInternal(address) && !allowedLoopback) {
                throw invalid("PRIVATE_ADDRESS", "url must point to a public address");
            }
        }
        boolean httpAllowed = mode == Mode.TEST && allLoopback;
        if (!scheme.equals("https") && !httpAllowed) {
            throw invalid("INSECURE_URL", "url must use https (http only for localhost in test mode)");
        }
    }

    /** Re-check at send time (DNS may have changed since the endpoint was saved). */
    public static boolean isAllowed(Mode mode, String url) {
        try {
            validate(mode, url);
            return true;
        } catch (FluxpayException e) {
            return false;
        }
    }

    private static boolean isInternal(InetAddress address) {
        if (address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || address.isAnyLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] raw = address.getAddress();
        boolean uniqueLocalV6 = address instanceof Inet6Address && (raw[0] & 0xFE) == 0xFC;
        boolean carrierGradeNat = raw.length == 4 && (raw[0] & 0xFF) == 100 && (raw[1] & 0xC0) == 64;
        return uniqueLocalV6 || carrierGradeNat;
    }

    private static FluxpayException invalid(String code, String message) {
        return FluxpayException.validation(FIELD, code, message);
    }
}
