package com.fluxpay.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Request builders and fixtures shared by integration tests. */
public final class TestMerchants {

    private TestMerchants() {}

    public static MockHttpServletRequestBuilder jsonPost(String url, String body) {
        return MockMvcRequestBuilders.post(url)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
