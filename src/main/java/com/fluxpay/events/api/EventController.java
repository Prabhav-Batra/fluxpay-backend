package com.fluxpay.events.api;

import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.events.service.EventQueryService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard/events")
public class EventController {

    private final EventQueryService eventQueryService;

    public EventController(EventQueryService eventQueryService) {
        this.eventQueryService = eventQueryService;
    }

    @GetMapping
    public CursorPage<EventResponse> list(
            TenantContext tenant,
            @RequestParam(name = "starting_after", required = false) String startingAfter,
            @RequestParam(required = false) Integer limit) {
        return eventQueryService
                .list(tenant, PageQuery.of(IdPrefix.EVENT, startingAfter, limit))
                .map(EventResponse::from);
    }

    @GetMapping("/{id}")
    public EventDetailResponse get(TenantContext tenant, @PathVariable String id) {
        return EventDetailResponse.from(eventQueryService.get(tenant, parseId(id)));
    }

    @PostMapping("/{id}/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Integer> resend(TenantContext tenant, @PathVariable String id) {
        return Map.of("deliveries_created", eventQueryService.resend(tenant, parseId(id)));
    }

    private static UUID parseId(String id) {
        return PublicId.parseOrNotFound(IdPrefix.EVENT, id, "EVENT_NOT_FOUND", "Event not found");
    }
}
