package com.fluxpay.gatewayframework.service;

import com.fluxpay.external.gateway.PaymentGatewayPort;
import com.fluxpay.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GatewayRouter {

    private final Map<String, PaymentGatewayPort> gateways;

    public GatewayRouter(List<PaymentGatewayPort> gatewayList) {
        this.gateways = gatewayList.stream()
                .collect(Collectors.toMap(
                        port -> port.getProviderName().toUpperCase(),
                        Function.identity()
                ));
    }

    public PaymentGatewayPort route(String preferredProvider) {
        if (preferredProvider == null || preferredProvider.isBlank()) {
            // Default routing logic could go here (e.g. cheapest provider)
            // For now, default to CASHFREE
            preferredProvider = "CASHFREE";
        }

        PaymentGatewayPort gateway = gateways.get(preferredProvider.toUpperCase());
        
        if (gateway == null) {
            log.error("Requested gateway provider not found: {}", preferredProvider);
            throw new BusinessException("Gateway provider not configured", "GATEWAY_UNAVAILABLE");
        }
        
        return gateway;
    }
}
