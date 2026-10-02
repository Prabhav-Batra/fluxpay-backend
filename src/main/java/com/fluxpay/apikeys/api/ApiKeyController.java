package com.fluxpay.apikeys.api;

import com.fluxpay.apikeys.service.ApiKeyService;
import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard/api_keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @GetMapping
    public Map<String, List<ApiKeyResponse>> list(TenantContext tenant) {
        return Map.of(
                "data",
                apiKeyService.list(tenant).stream().map(ApiKeyResponse::from).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyResponse create(TenantContext tenant) {
        return ApiKeyResponse.from(apiKeyService.create(tenant));
    }

    @PostMapping("/{id}/roll")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyResponse roll(TenantContext tenant, @PathVariable String id) {
        return ApiKeyResponse.from(apiKeyService.roll(tenant, parseId(id)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(TenantContext tenant, @PathVariable String id) {
        apiKeyService.revoke(tenant, parseId(id));
    }

    private static UUID parseId(String id) {
        return PublicId.parse(IdPrefix.API_KEY, id)
                .orElseThrow(() -> FluxpayException.notFound("API_KEY_NOT_FOUND", "API key not found"));
    }
}
