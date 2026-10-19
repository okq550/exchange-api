package org.okq550.exchange_api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    enum Side { BUY, SELL };
    record Quote(String symbol, long bidInCents, long askInCents) {}
    record OrderRequest(
            @NotBlank String symbol,
            @NotNull Side side,
            @Positive int quantity,
            @Positive long priceInCents) {}

    @GetMapping("/quote")
    public Quote getQoute() {
        return new Quote("AAPL", 10150, 10175);
    }

    @PostMapping("/orders")
    public String placeOrder(@Valid @RequestBody OrderRequest request) {
        return "Received " + request.side() + " " + request.quantity() + " " + request.symbol()
                + " @ " + request.priceInCents();
    }
}
