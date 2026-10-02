package com.fluxpay.events.service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.hc.client5.http.DnsResolver;

/**
 * Validates the addresses the HTTP client will actually connect to, closing the DNS-rebinding gap between the
 * URL check and the connection.
 */
public class GuardedDnsResolver implements DnsResolver {

    private final DnsResolver delegate;
    private final boolean allowLoopback;

    public GuardedDnsResolver(DnsResolver delegate, boolean allowLoopback) {
        this.delegate = delegate;
        this.allowLoopback = allowLoopback;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] addresses = delegate.resolve(host);
        for (InetAddress address : addresses) {
            boolean permittedLoopback = allowLoopback && address.isLoopbackAddress();
            if (AddressClassifier.isInternal(address) && !permittedLoopback) {
                throw new UnknownHostException("Webhook host resolves to a blocked address: " + host);
            }
        }
        return addresses;
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        return delegate.resolveCanonicalHostname(host);
    }
}
