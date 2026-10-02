package com.fluxpay.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.events.service.AddressClassifier;
import com.fluxpay.events.service.GuardedDnsResolver;
import com.fluxpay.events.service.WebhookUrlPolicy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.hc.client5.http.DnsResolver;
import org.junit.jupiter.api.Test;

class AddressGuardTest {

    private static boolean internal(String ip) throws UnknownHostException {
        return AddressClassifier.isInternal(InetAddress.getByName(ip));
    }

    @Test
    void should_classify_reserved_and_tunnelled_ranges_as_internal() throws Exception {
        for (String ip : new String[] {
            "127.0.0.1",
            "10.0.0.1",
            "169.254.169.254",
            "0.1.2.3",
            "198.18.0.1",
            "240.0.0.1",
            "100.64.0.1",
            "::1",
            "fd00::1",
            "::a9fe:a9fe",
            "64:ff9b::a9fe:a9fe",
            "2002:a9fe:a9fe::1"
        }) {
            assertThat(internal(ip)).as(ip).isTrue();
        }
        assertThat(internal("93.184.216.34")).isFalse();
        assertThat(internal("64:ff9b::5db8:d822")).isFalse();
    }

    @Test
    void should_refuse_to_resolve_internal_addresses_at_connect_time() {
        DnsResolver rebinding = new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                return new InetAddress[] {InetAddress.getByName("169.254.169.254")};
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host;
            }
        };

        assertThatThrownBy(() -> new GuardedDnsResolver(rebinding, false).resolve("hooks.example.com"))
                .isInstanceOf(UnknownHostException.class);
    }

    @Test
    void should_allow_loopback_only_when_enabled_by_configuration() {
        assertThatThrownBy(() -> WebhookUrlPolicy.validate(Mode.TEST, "http://localhost:9999/h", false))
                .isInstanceOf(FluxpayException.class)
                .extracting(e -> ((FluxpayException) e).details().get(0).code())
                .isEqualTo("PRIVATE_ADDRESS");
        assertThatCode(() -> WebhookUrlPolicy.validate(Mode.TEST, "http://localhost:9999/h", true))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> WebhookUrlPolicy.validate(Mode.LIVE, "https://localhost/h", true))
                .isInstanceOf(FluxpayException.class);
    }
}
