package com.fluxpay.admin.api;

import com.fluxpay.admin.service.AdminMerchantService;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.ledger.service.NewPayout;
import com.fluxpay.merchants.api.MerchantResponse;
import com.fluxpay.merchants.domain.MerchantStatus;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** PLATFORM_ADMIN only (enforced in SecurityConfig for /api/v1/admin/**). */
@RestController
@RequestMapping("/api/v1/admin/merchants")
public class AdminMerchantController {

    private final AdminMerchantService adminService;

    public AdminMerchantController(AdminMerchantService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public CursorPage<MerchantResponse> list(
            @RequestParam(name = "starting_after", required = false) String startingAfter,
            @RequestParam(required = false) Integer limit) {
        return adminService
                .list(PageQuery.of(IdPrefix.MERCHANT, startingAfter, limit))
                .map(MerchantResponse::from);
    }

    @GetMapping("/{id}")
    public AdminResponses.MerchantDetail get(@PathVariable String id) {
        return AdminResponses.MerchantDetail.from(adminService.get(parseId(id)));
    }

    @PatchMapping("/{id}")
    public MerchantResponse update(@PathVariable String id, @Valid @RequestBody AdminRequests.UpdateMerchant body) {
        MerchantStatus status = body.status() == null
                ? null
                : MerchantStatus.valueOf(body.status().toUpperCase(Locale.ROOT));
        return MerchantResponse.from(adminService.update(parseId(id), body.platformFeeBps(), status));
    }

    @PostMapping("/{id}/payouts")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminResponses.Payout recordPayout(
            @PathVariable String id, @Valid @RequestBody AdminRequests.RecordPayout body, Principal admin) {
        NewPayout payout =
                new NewPayout(body.amount(), body.reference().trim(), body.paidAt(), UUID.fromString(admin.getName()));
        return AdminResponses.Payout.from(adminService.recordPayout(parseId(id), mode(body.mode()), payout));
    }

    @GetMapping("/{id}/payouts")
    public CursorPage<AdminResponses.Payout> payouts(
            @PathVariable String id,
            @RequestParam(required = false) String mode,
            @RequestParam(name = "starting_after", required = false) String startingAfter,
            @RequestParam(required = false) Integer limit) {
        PageQuery query = PageQuery.of(IdPrefix.PAYOUT, startingAfter, limit);
        return adminService.payouts(parseId(id), mode(mode), query).map(AdminResponses.Payout::from);
    }

    private static Mode mode(String value) {
        return value == null ? Mode.LIVE : Mode.parse(value).orElse(Mode.LIVE);
    }

    private static UUID parseId(String id) {
        return PublicId.parseOrNotFound(IdPrefix.MERCHANT, id, "MERCHANT_NOT_FOUND", "Merchant not found");
    }
}
