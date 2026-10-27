package org.okq550.exchange_api.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.okq550.exchange_api.domain.Order;
import org.okq550.exchange_api.domain.Side;
import org.okq550.exchange_api.domain.Trade;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTest {

    // new Order(orderId, accountId, symbol, side, quantity, priceInCents)
    // new Trade(buyerOrderId, sellerOrderId, buyAccountId, sellAccountId, buyLimitInCents,
    //           symbol, quantity, priceInCents)

    @Test
    @DisplayName("Prices that don't cross: nothing trades, both orders rest")
    void buyBelowBestAskRestsWithoutTrading() {
        MatchingEngine engine = new MatchingEngine();
        engine.submit(new Order(1, 1, "AAPL", Side.SELL, 100, 10200));

        List<Trade> trades = engine.submit(new Order(2, 2, "AAPL", Side.BUY, 100, 10150));

        assertTrue(trades.isEmpty());
        assertEquals(10150L, engine.bestBid());
        assertEquals(10200L, engine.bestAsk());
    }

    @Test
    @DisplayName("A buy takes the cheapest ask at the resting price")
    void buyCrossesBestAskTradesAtRestingPrice() {
        MatchingEngine engine = new MatchingEngine();
        engine.submit(new Order(1, 1, "AAPL", Side.SELL, 100, 10100));   // order 1: account 1 sells @ 101.00
        engine.submit(new Order(2, 2, "AAPL", Side.SELL, 100, 10150));   // order 2: account 2 sells @ 101.50

        List<Trade> trades = engine.submit(new Order(3, 1, "AAPL", Side.BUY, 100, 10150));   // order 3: account 1 buys, limit 101.50

        assertEquals(1, trades.size());
        // buy order 3 (account 1, limit 10150) matched sell order 1 (account 1)
        assertEquals(new Trade(3, 1, 1, 1, 10150, "AAPL", 100, 10100), trades.getFirst());
        assertNull(engine.bestBid());
        assertEquals(10150L, engine.bestAsk());
    }

    @Test
    @DisplayName("A partial fill leaves the resting order's remainder in the book")
    void buyPartiallyFillsRestingAskAndAskKeepsRemainder() {
        MatchingEngine engine = new MatchingEngine();
        engine.submit(new Order(1, 1, "AAPL", Side.SELL, 80, 10100));    // order 1: account 1 sells 80

        List<Trade> trades = engine.submit(new Order(2, 2, "AAPL", Side.BUY, 50, 10100));   // order 2: account 2 buys 50, limit 101.00

        assertEquals(1, trades.size());
        // buy order 2 (account 2, limit 10100) matched sell order 1 (account 1)
        assertEquals(new Trade(2, 1, 2, 1, 10100, "AAPL", 50, 10100), trades.getFirst());
        assertNull(engine.bestBid());
        assertEquals(10100L, engine.bestAsk());
    }

    @Test
    @DisplayName("A sell sweeps two bid levels, best first")
    void sellSweepsTwoBidLevelsAndPartiallyFillsSecond() {
        MatchingEngine engine = new MatchingEngine();
        engine.submit(new Order(1, 1, "AAPL", Side.BUY, 200, 10100));    // order 1: account 1 bids @ 101.00
        engine.submit(new Order(2, 2, "AAPL", Side.BUY, 100, 10050));    // order 2: account 2 bids @ 100.50

        List<Trade> trades = engine.submit(new Order(3, 1, "AAPL", Side.SELL, 250, 10050));   // order 3: account 1 sells

        assertEquals(2, trades.size());
        // buy order 1 (account 1, limit 10100) matched sell order 3 (account 1)
        assertEquals(new Trade(1, 3, 1, 1, 10100, "AAPL", 200, 10100), trades.get(0));
        // buy order 2 (account 2, limit 10050) matched sell order 3 (account 1)
        assertEquals(new Trade(2, 3, 2, 1, 10050, "AAPL", 50, 10050), trades.get(1));
        assertEquals(10050L, engine.bestBid());
        assertNull(engine.bestAsk());
    }

    @Test
    @DisplayName("Each trade uses the resting bid's price, not the seller's lower limit")
    void sellBelowAllBidsTradesAtEachRestingPrice() {
        MatchingEngine engine = new MatchingEngine();
        engine.submit(new Order(1, 1, "AAPL", Side.BUY, 100, 10100));    // order 1: account 1 bids @ 101.00
        engine.submit(new Order(2, 2, "AAPL", Side.BUY, 500, 10050));    // order 2: account 2 bids @ 100.50

        List<Trade> trades = engine.submit(new Order(3, 1, "AAPL", Side.SELL, 300, 10000));   // order 3: account 1 sells

        assertEquals(2, trades.size());
        // buy order 1 (account 1, limit 10100) matched sell order 3 (account 1)
        assertEquals(new Trade(1, 3, 1, 1, 10100, "AAPL", 100, 10100), trades.getFirst());
        // buy order 2 (account 2, limit 10050) matched sell order 3 (account 1)
        assertEquals(new Trade(2, 3, 2, 1, 10050, "AAPL", 200, 10050), trades.get(1));
        assertEquals(10050L, engine.bestBid());
        assertNull(engine.bestAsk());
    }

    @Test
    @DisplayName("Incoming sell: the resting buy is recorded as the buyer")
    void incomingSellRecordsRestingBuyAsBuyer() {
        MatchingEngine engine = new MatchingEngine();
        engine.submit(new Order(1, 1, "AAPL", Side.BUY, 10, 10000));     // order 1: account 1 buys (rests)

        List<Trade> trades = engine.submit(new Order(2, 2, "AAPL", Side.SELL, 10, 10000));   // order 2: account 2 sells

        Trade t = trades.getFirst();
        assertEquals(1L, t.buyerOrderId());
        assertEquals(2L, t.sellerOrderId());
        assertEquals(1L, t.buyAccountId());
        assertEquals(2L, t.sellAccountId());
        assertEquals(10000L, t.buyLimitInCents());
    }

    @Test
    @DisplayName("Incoming buy: the resting sell is recorded as the seller")
    void incomingBuyRecordsRestingSellAsSeller() {
        MatchingEngine engine = new MatchingEngine();
        engine.submit(new Order(1, 1, "AAPL", Side.SELL, 10, 10000));    // order 1: account 1 sells (rests)

        List<Trade> trades = engine.submit(new Order(2, 2, "AAPL", Side.BUY, 10, 10000));    // order 2: account 2 buys

        Trade t = trades.getFirst();
        assertEquals(2L, t.buyerOrderId());
        assertEquals(1L, t.sellerOrderId());
        assertEquals(2L, t.buyAccountId());
        assertEquals(1L, t.sellAccountId());
        assertEquals(10000L, t.buyLimitInCents());
    }

    @Test
    @DisplayName("A redelivered order is ignored: it can't rest or trade twice")
    void duplicateOrderIsIgnored() {
        MatchingEngine engine = new MatchingEngine();
        engine.submit(new Order(1, 1, "AAPL", Side.SELL, 100, 10000));   // order 1: sell 100 @ 100.00
        engine.submit(new Order(1, 1, "AAPL", Side.SELL, 100, 10000));   // order 1 again: a redelivery

        List<Trade> trades = engine.submit(new Order(2, 2, "AAPL", Side.BUY, 200, 10000));   // buy 200 @ 100.00

        assertEquals(1, trades.size());                                   // only ONE sell of 100 was in the book
        assertEquals(new Trade(2, 1, 2, 1, 10000, "AAPL", 100, 10000), trades.getFirst());
        assertEquals(10000L, engine.bestBid());                           // the buy's other 100 rests
        assertNull(engine.bestAsk());                                     // no second copy of the sell left over
    }

}