package com.fluxpay.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background jobs run on one instance only; set FLUXPAY_JOBS_ENABLED=false on any additional instance. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "fluxpay.jobs", name = "enabled", havingValue = "true")
public class SchedulingConfig {}
