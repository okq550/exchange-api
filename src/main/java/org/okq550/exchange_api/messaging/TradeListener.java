package org.okq550.exchange_api.messaging;

import org.okq550.exchange_api.account.AccountService;
import org.okq550.exchange_api.domain.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TradeListener {

    private final AccountService accountService;
    private static final Logger log = LoggerFactory.getLogger(TradeListener.class);

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
            log.warn("Skipping malformed trade: {}", message, e);
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

        log.info("Trade settled buyOrder={} sellOrder={} qty={} price={}", trade.buyerOrderId(), trade.sellerOrderId(), trade.quantity(), trade.priceInCents());
    }
}