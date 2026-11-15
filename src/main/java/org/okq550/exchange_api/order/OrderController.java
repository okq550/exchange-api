package org.okq550.exchange_api.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.okq550.exchange_api.account.AccountService;
import org.okq550.exchange_api.domain.Side;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.concurrent.ExecutionException;

@RestController
public class OrderController {
    private final ExchangeService exchangeService;
    private final AccountService accountService;

    public OrderController(ExchangeService exchangeService, AccountService accountService) {
        this.exchangeService = exchangeService;
        this.accountService = accountService;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OrderAccepted placeOrder(@Valid @RequestBody OrderRequest request, Principal principal) {
        accountService.requireOwner(request.accountId(), principal.getName());
        return exchangeService.place(request.accountId(), request.symbol(), request.side(),
                request.quantity(), request.priceInCents());
    }

    @DeleteMapping("/orders/{id}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CancelAccepted cancelOrder(@PathVariable long id, Principal principal) {
        return exchangeService.cancel(id, principal.getName());
    }

    record OrderRequest(
            @Positive long accountId,
            @NotBlank String symbol,
            @NotNull Side side,
            @Positive int quantity,
            @Positive long priceInCents
    ) {
    }
}
