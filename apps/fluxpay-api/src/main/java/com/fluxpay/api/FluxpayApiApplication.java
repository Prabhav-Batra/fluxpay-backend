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
}
