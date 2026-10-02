package com.fluxpay.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void should_propagate_incoming_id_when_header_is_valid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "req-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seen.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(seen.get()).isEqualTo("req-123");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("req-123");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void should_generate_new_id_when_header_is_missing_or_unsafe() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "bad value\nwith newline");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).matches("[0-9a-f-]{36}");
    }
}
