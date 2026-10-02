package com.fluxpay.events.service;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/** Addresses webhooks must never reach: private, loopback, link-local, reserved, and IPv4 tunnelled in IPv6. */
public final class AddressClassifier {

    private AddressClassifier() {}

    public static boolean isInternal(InetAddress address) {
        if (address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || address.isAnyLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] raw = address.getAddress();
        if (address instanceof Inet4Address) {
            return isReservedV4(raw);
        }
        if (address instanceof Inet6Address) {
            if ((raw[0] & 0xFE) == 0xFC) {
                return true; // fc00::/7 unique local
            }
            byte[] embedded = embeddedV4(raw);
            return embedded != null && isInternal(v4(embedded));
        }
        return false;
    }

    private static boolean isReservedV4(byte[] ip) {
        int a = ip[0] & 0xFF;
        int b = ip[1] & 0xFF;
        return a == 0 // 0.0.0.0/8
                || (a == 100 && (b & 0xC0) == 64) // 100.64.0.0/10 carrier-grade NAT
                || (a == 198 && (b & 0xFE) == 18) // 198.18.0.0/15 benchmarking
                || a >= 240; // 240.0.0.0/4 reserved and broadcast
    }

    /** IPv4 carried inside IPv4-compatible ::/96, NAT64 64:ff9b::/96 or 6to4 2002::/16 addresses. */
    private static byte[] embeddedV4(byte[] ip) {
        boolean compatible = Arrays.equals(Arrays.copyOfRange(ip, 0, 12), new byte[12]);
        boolean nat64 = (ip[0] & 0xFF) == 0x00
                && (ip[1] & 0xFF) == 0x64
                && (ip[2] & 0xFF) == 0xFF
                && (ip[3] & 0xFF) == 0x9B
                && Arrays.equals(Arrays.copyOfRange(ip, 4, 12), new byte[8]);
        if (compatible || nat64) {
            return Arrays.copyOfRange(ip, 12, 16);
        }
        if ((ip[0] & 0xFF) == 0x20 && (ip[1] & 0xFF) == 0x02) {
            return Arrays.copyOfRange(ip, 2, 6);
        }
        return null;
    }

    private static InetAddress v4(byte[] bytes) {
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }
}
