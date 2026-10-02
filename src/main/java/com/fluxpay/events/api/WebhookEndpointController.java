package com.fluxpay.events.api;

import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.events.service.WebhookEndpointService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard/webhook_endpoints")
public class WebhookEndpointController {

    private final WebhookEndpointService endpointService;

    public WebhookEndpointController(WebhookEndpointService endpointService) {
        this.endpointService = endpointService;
    }

    @GetMapping
    public Map<String, List<WebhookEndpointResponse>> list(TenantContext tenant) {
        return Map.of(
                "data",
                endpointService.list(tenant).stream()
                        .map(WebhookEndpointResponse::from)
                        .toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WebhookEndpointResponse create(TenantContext tenant, @Valid @RequestBody CreateWebhookEndpointRequest body) {
        return WebhookEndpointResponse.from(endpointService.create(tenant, body.url()));
    }

    @PatchMapping("/{id}")
    public WebhookEndpointResponse update(
            TenantContext tenant, @PathVariable String id, @RequestBody UpdateWebhookEndpointRequest body) {
        return WebhookEndpointResponse.from(endpointService.update(tenant, parseId(id), body.url(), body.enabled()));
    }

    @PostMapping("/{id}/roll_secret")
    public WebhookEndpointResponse rollSecret(TenantContext tenant, @PathVariable String id) {
        return WebhookEndpointResponse.from(endpointService.rollSecret(tenant, parseId(id)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(TenantContext tenant, @PathVariable String id) {
        endpointService.delete(tenant, parseId(id));
    }

    @PostMapping("/{id}/test")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EventResponse sendTest(TenantContext tenant, @PathVariable String id) {
        return EventResponse.from(endpointService.sendTest(tenant, parseId(id)));
    }

    private static UUID parseId(String id) {
        return PublicId.parseOrNotFound(
                IdPrefix.WEBHOOK_ENDPOINT, id, "WEBHOOK_ENDPOINT_NOT_FOUND", "Webhook endpoint not found");
    }
}
