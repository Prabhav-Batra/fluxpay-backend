package com.fluxpay.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.fluxpay"})
@EntityScan(basePackages = {"com.fluxpay"})
@EnableJpaRepositories(basePackages = {"com.fluxpay"})
public class FluxpayApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FluxpayApiApplication.class, args);
    }

    @org.springframework.context.annotation.Bean
    public org.springframework.boot.CommandLineRunner seedWebhook(
            com.fluxpay.webhook.repository.WebhookEndpointRepository repo) {
        return args -> {
            java.util.UUID merchantId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000000");
            if (repo.findByMerchantIdAndActiveTrue(merchantId).isEmpty()) {
                com.fluxpay.webhook.entity.WebhookEndpoint endpoint = com.fluxpay.webhook.entity.WebhookEndpoint.builder()
                        .merchantId(merchantId)
                        .url("https://webhook.site/26cd7d36-e82b-4573-a6cd-c313a48e7ff7") // Example non-existent endpoint
                        .secretKey("whsec_testsecretkey1234567890abcdef")
                        .active(true)
                        .build();
                repo.save(endpoint);
                System.out.println("Seeded test webhook endpoint for merchant " + merchantId);
            }
        };
    }
}
