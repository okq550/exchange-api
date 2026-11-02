package org.okq550.exchange_api.messaging;

import org.okq550.exchange_api.account.AccountService;
import org.okq550.exchange_api.domain.Trade;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TradeListener {

    private final AccountService accountService;

    public TradeListener(AccountService accountService) {
        this.accountService = accountService;
    }

    // The Account service's view of the world: every trade, settled once, in order.
    @KafkaListener(topics = "trades", groupId = "account-service")
    public void onTrade(String message) {
        Trade trade;
        try {
            trade = Trade.fromWire(message);
        } catch (RuntimeException e) {
            System.out.println("Skipping malformed trade: " + message);
            return;
        }

        accountService.settleTrade(
                trade.buyerOrderId(),
                trade.sellerOrderId(),
                trade.buyAccountId(),
                trade.sellAccountId(),
                trade.symbol(),
                trade.quantity(),
                trade.priceInCents(),
                trade.buyLimitInCents());
    }
}