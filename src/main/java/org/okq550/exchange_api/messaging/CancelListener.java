package org.okq550.exchange_api.messaging;

import org.okq550.exchange_api.AccountService;
import org.okq550.exchange_api.domain.Order;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CancelListener {

    private final AccountService accountService;

    public CancelListener(AccountService accountService) {
        this.accountService = accountService;
    }

    // Each message is the part of an order that was still open when it was cancelled.
    @KafkaListener(topics = "cancels", groupId = "account-service")
    public void onCancel(String message) {
        Order remaining;
        try {
            remaining = Order.fromWire(message);   // e.g. "7|1|AAPL|BUY|70|10100"
        } catch (RuntimeException e) {
            System.out.println("Skipping malformed cancel: " + message);
            return;
        }

        accountService.releaseCancelled(remaining);
    }
}