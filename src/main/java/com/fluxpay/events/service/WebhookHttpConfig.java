package com.fluxpay.events.service;

import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class WebhookHttpConfig {

    /**
     * Webhook HTTP client: every connection goes through {@link GuardedDnsResolver}, redirects and automatic
     * retries are disabled, and connect/read timeouts are bounded.
     */
    @Bean
    public RestClient webhookRestClient(WebhookProperties properties) {
        ConnectionConfig connection = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(properties.connectTimeout()))
                .setSocketTimeout(Timeout.of(properties.readTimeout()))
                .build();
        CloseableHttpClient client = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(
                                new GuardedDnsResolver(SystemDefaultDnsResolver.INSTANCE, properties.allowLoopback()))
                        .setDefaultConnectionConfig(connection)
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.of(properties.readTimeout()))
                        .build())
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .build();
        return RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(client))
                .build();
    }
}
