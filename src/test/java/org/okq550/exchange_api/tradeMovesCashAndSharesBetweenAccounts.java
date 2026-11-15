package org.okq550.exchange_api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.okq550.exchange_api.account.Account;
import org.okq550.exchange_api.account.AccountService;
import org.okq550.exchange_api.domain.Order;
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

    @Autowired AccountService accountService;
    @Autowired ExchangeService exchangeService;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void tradeMovesCashAndSharesBetweenAccounts() {
        long buyer  = accountService.create("alice", 1_000_000, 0).id();
        long seller = accountService.create("bob", 0, 50).id();

        long buyOrderId  = exchangeService.place(buyer,  "AAPL", Side.BUY,  10, 10_100).orderId();
        long sellOrderId = exchangeService.place(seller, "AAPL", Side.SELL, 10, 10_000).orderId();

        // Play the engine: the buy rested first, so they trade at its price, 101.00
        Trade trade = new Trade(buyOrderId, sellOrderId, buyer, seller, 10_100, "AAPL", 10, 10_100);
        kafkaTemplate.send("trades", "AAPL", trade.toWire());

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
        assertEquals(10 * 9_000, accountService.view(buyer).reservedInCents());

        // The API side of a cancel: accepted, and queued for the engine
        exchangeService.cancel(orderId, "alice");

        // Play the engine: it found the order resting with all 10 shares and removed it
        Order remaining = new Order(orderId, buyer, "AAPL", Side.BUY, 10, 9_000);
        kafkaTemplate.send("cancels", "AAPL", remaining.toWire());

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
        long buyer  = accountService.create("alice", 1_000_000, 0).id();
        long seller = accountService.create("bob", 0, 50).id();

        long buyOrderId  = exchangeService.place(buyer,  "AAPL", Side.BUY,  10, 8_000).orderId();
        long sellOrderId = exchangeService.place(seller, "AAPL", Side.SELL, 10, 8_000).orderId();

        Trade trade = new Trade(buyOrderId, sellOrderId, buyer, seller, 8_000, "AAPL", 10, 8_000);

        // The engine publishes the trade...
        kafkaTemplate.send("trades", "AAPL", trade.toWire());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertEquals(1_000_000 - 80_000, accountService.view(buyer).cashInCents()));

        // ...and Kafka redelivers it
        kafkaTemplate.send("trades", "AAPL", trade.toWire());

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Account.AccountView b = accountService.view(buyer);
            assertEquals(1_000_000 - 80_000, b.cashInCents());
            assertEquals(10, b.shares());

            Account.AccountView s = accountService.view(seller);
            assertEquals(80_000, s.cashInCents());
            assertEquals(40, s.shares());
        });
    }
}