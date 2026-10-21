package org.okq550.exchange_api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.okq550.exchange_api.account.Account;
import org.springframework.web.bind.annotation.*;

@RestController
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/accounts")
    public Account.AccountView createAccount(@Valid @RequestBody AccountRequest request) {
        return accountService.create(request.initialCashInCents(), request.initialShares());
    }

    @GetMapping("/accounts/{id}")
    public Account.AccountView getAccount(@PathVariable long id) {
        return accountService.find(id).view();
    }

    private record AccountRequest(@PositiveOrZero long initialCashInCents,
                                  @PositiveOrZero long initialShares) {
    }
}
