package org.okq550.exchange_api.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.okq550.exchange_api.trade.TradeRecord;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/accounts")
    public Account.AccountView createAccount(@Valid @RequestBody AccountRequest request, Principal principal) {
        return accountService.create(principal.getName(), request.initialCashInCents(), request.initialShares());
    }

    @GetMapping("/accounts/{id}")
    public Account.AccountView getAccount(@PathVariable long id, Principal principal) {
        accountService.requireOwner(id, principal.getName());
        return accountService.view(id);
    }

    @GetMapping("/accounts/{id}/trades")
    public List<TradeRecord> getTrades(@PathVariable long id, Principal principal) {
        accountService.requireOwner(id, principal.getName());
        return accountService.trades(id);
    }

    private record AccountRequest(@PositiveOrZero long initialCashInCents,
                                  @PositiveOrZero long initialShares) {
    }
}
