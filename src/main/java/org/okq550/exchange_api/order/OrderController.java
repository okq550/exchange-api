package org.okq550.exchange_api.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.okq550.exchange_api.domain.Side;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutionException;

@RestController
public class OrderController {
    private final ExchangeService exchangeService;

    public OrderController(ExchangeService exchangeService) {
        this.exchangeService = exchangeService;
    }

    @PostMapping("/orders")
    public OrderAccepted placeOrder(@Valid @RequestBody OrderRequest request) throws ExecutionException, InterruptedException {
        return exchangeService.place(request.accountId(), request.symbol(), request.side(), request.quantity(), request.priceInCents());
    }

    @DeleteMapping("/orders/{id}")
    public CancelAccepted cancelOrder(@Valid @PathVariable("id") long orderId) throws InterruptedException, ExecutionException {
        return exchangeService.cancel(orderId);
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
