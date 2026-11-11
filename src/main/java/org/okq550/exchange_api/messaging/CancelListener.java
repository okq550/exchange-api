package org.okq550.exchange_api.messaging;

import org.okq550.exchange_api.account.AccountService;
import org.okq550.exchange_api.domain.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CancelListener {

    private static final Logger log = LoggerFactory.getLogger(CancelListener.class);

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
            log.warn("Skipping malformed cancel: {}", message, e);
            return;
        }

        accountService.releaseCancelled(remaining);
    }
}