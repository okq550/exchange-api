package org.okq550.exchange_api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.okq550.exchange_api.account.Account;
import org.okq550.exchange_api.trade.TradeRecord;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        return accountService.view(id);
    }

    @GetMapping("/accounts/{id}/trades")
    public List<TradeRecord> getTrades(@PathVariable long id) {
        return accountService.trades(id);
    }

    private record AccountRequest(@PositiveOrZero long initialCashInCents,
                                  @PositiveOrZero long initialShares) {
    }
}
