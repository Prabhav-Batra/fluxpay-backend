package com.fluxpay.ledger.api;

import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.ledger.service.LedgerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class BalanceController {

    private final LedgerService ledgerService;

    public BalanceController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/balance")
    public BalanceResponse balance(TenantContext tenant) {
        return BalanceResponse.from(ledgerService.balance(tenant));
    }

    @GetMapping("/ledger_entries")
    public CursorPage<LedgerEntryResponse> entries(
            TenantContext tenant,
            @RequestParam(name = "starting_after", required = false) String startingAfter,
            @RequestParam(required = false) Integer limit) {
        PageQuery query = PageQuery.of(IdPrefix.LEDGER_ENTRY, startingAfter, limit);
        return ledgerService.entries(tenant, query).map(LedgerEntryResponse::from);
    }
}
