package org.okq550.exchange_api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.okq550.exchange_api.account.Account;
import org.okq550.exchange_api.account.AccountService;
import org.okq550.exchange_api.domain.Side;
import org.okq550.exchange_api.domain.Trade;
import org.okq550.exchange_api.order.ExchangeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SettlementIntegrationTest {

    @Autowired
    AccountService accountService;
    @Autowired
    ExchangeService exchangeService;
    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void tradeMovesCashAndSharesBetweenAccounts() {
        long buyer = accountService.create( "alice", 1_000_000, 0).id();
        long seller = accountService.create("bob", 0, 50).id();

        exchangeService.place(buyer, "AAPL", Side.BUY, 10, 10_100);
        exchangeService.place(seller, "AAPL", Side.SELL, 10, 10_000);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Account.AccountView b = accountService.view(buyer);
            assertEquals(1_000_000 - 101_000, b.cashInCents());
            assertEquals(0, b.reservedInCents());
            assertEquals(10, b.shares());

            Account.AccountView s = accountService.view(seller);
            assertEquals(101_000, s.cashInCents());
            assertEquals(40, s.shares());
            assertEquals(0, s.reservedShares());
        });
    }

    @Test
    void cancellingRestingBuyReleasesItsFullReservation() {
        long buyer = accountService.create("alice", 1_000_000, 0).id();

        long orderId = exchangeService.place(buyer, "AAPL", Side.BUY, 10, 9_000).orderId();

        // Reserved synchronously inside place()
        assertEquals(10 * 9_000, accountService.view(buyer).reservedInCents());

        // Safe to cancel immediately: it queues behind the order in the same partition
        exchangeService.cancel(orderId, "alice");

        // The release happens asynchronously, once the CancelListener processes the cancels message
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Account.AccountView after = accountService.view(buyer);
            assertEquals(1_000_000, after.cashInCents());
            assertEquals(0, after.reservedInCents());
            assertEquals(1_000_000, after.availableInCents());
        });
    }

    @Test
    @DisplayName("A redelivered trade message is settled only once")
    void duplicateTradeMessageIsSettledOnlyOnce() {
        long buyer = accountService.create("alice", 1_000_000, 0).id();   // $10,000
        long seller = accountService.create("bob", 0, 50).id();          // 50 shares

        // A price no other test uses, so this pair can only match each other
        long buyOrderId = exchangeService.place(buyer, "AAPL", Side.BUY, 10, 8_000).orderId();
        long sellOrderId = exchangeService.place(seller, "AAPL", Side.SELL, 10, 8_000).orderId();

        // 1. Wait for the real trade to settle: 10 @ 80.00 = 80,000 cents
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertEquals(1_000_000 - 80_000, accountService.view(buyer).cashInCents());
            assertEquals(80_000, accountService.view(seller).cashInCents());
        });

        // 2. Redeliver exactly the same trade, as Kafka would after a crash
        Trade sameTrade = new Trade(buyOrderId, sellOrderId, buyer, seller, 8_000, "AAPL", 10, 8_000);
        kafkaTemplate.send("trades", "AAPL", sameTrade.toWire());

        // 3. For 2 whole seconds, the balances must stay exactly as they were
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Account.AccountView b = accountService.view(buyer);
            assertEquals(1_000_000 - 80_000, b.cashInCents());
            assertEquals(10, b.shares());
            assertEquals(0, b.reservedInCents());

            Account.AccountView s = accountService.view(seller);
            assertEquals(80_000, s.cashInCents());
            assertEquals(40, s.shares());
            assertEquals(0, s.reservedShares());
        });
    }
}