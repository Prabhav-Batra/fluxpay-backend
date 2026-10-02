package com.fluxpay.identity.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapper implements ApplicationRunner {

    private final AdminBootstrapProperties properties;
    private final UserService userService;

    public AdminBootstrapper(AdminBootstrapProperties properties, UserService userService) {
        this.properties = properties;
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.isConfigured()) {
            userService.ensurePlatformAdmin(properties.bootstrapEmail(), properties.bootstrapPassword());
        }
    }
}
