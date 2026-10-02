package com.fluxpay.common.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fluxpay.common.web.SafeStringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public Module safeStringModule() {
        return new SimpleModule("fluxpay-safe-strings").addDeserializer(String.class, new SafeStringDeserializer());
    }
}
