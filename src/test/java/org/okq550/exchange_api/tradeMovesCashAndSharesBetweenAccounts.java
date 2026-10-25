package org.okq550.exchange_api;

import org.junit.jupiter.api.Test;
import org.okq550.exchange_api.account.Account;
import org.okq550.exchange_api.domain.Side;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SettlementIntegrationTest {

    @Autowired
    AccountService accountService;
    @Autowired
    ExchangeService exchangeService;

    @Test
    void tradeMovesCashAndSharesBetweenAccounts() throws Exception {
        long buyer = accountService.create(1_000_000, 0).id();   // $10,000
        long seller = accountService.create(0, 50).id();          // 50 shares

        exchangeService.place(buyer, "AAPL", Side.BUY, 10, 10_100);   // rests
        exchangeService.place(seller, "AAPL", Side.SELL, 10, 10_000); // trades 10 @ 10100

        Account.AccountView b = accountService.view(buyer);
        assertEquals(1_000_000 - 101_000, b.cashInCents());
        assertEquals(0, b.reservedInCents());
        assertEquals(10, b.shares());

        Account.AccountView s = accountService.view(seller);
        assertEquals(101_000, s.cashInCents());
        assertEquals(40, s.shares());
        assertEquals(0, s.reservedShares());
    }

    @Test
    void cancellingRestingBuyReleasesItsFullReservation() throws Exception {
        long buyer = accountService.create(1_000_000, 0).id();   // $10,000

        PlaceResult result = exchangeService.place(buyer, "AAPL", Side.BUY, 10, 10_100);   // rests
        long orderId = result.orderId();

        Account.AccountView before = accountService.view(buyer);
        assertTrue(result.trades().isEmpty());                   // nothing to trade against: it rests
        assertEquals(10 * 10_100, before.reservedInCents());     // 101,000 held

        exchangeService.cancel(orderId);

        Account.AccountView after = accountService.view(buyer);  // fresh snapshot after the cancel
        assertEquals(1_000_000, after.cashInCents());            // cash never moved
        assertEquals(0, after.reservedInCents());                // hold released
        assertEquals(1_000_000, after.availableInCents());       // everything available again
    }
}