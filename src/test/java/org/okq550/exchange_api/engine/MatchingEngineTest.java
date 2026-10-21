package org.okq550.exchange_api.engine;

import org.junit.jupiter.api.Test;
import org.okq550.exchange_api.domain.Order;
import org.okq550.exchange_api.domain.Side;
import org.okq550.exchange_api.domain.Trade;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTest {

    // new Order(orderId, accountId, symbol, side, quantity, priceInCents)
    // new Trade(buyerOrderId, sellerOrderId, symbol, quantity, priceInCents)

    @Test
    void buyBelowBestAskRestsWithoutTrading() {
        MatchingEngine engine = new MatchingEngine();
        engine.submit(new Order(1, 1, "AAPL", Side.SELL, 100, 10200));

        List<Trade> trades = engine.submit(new Order(2, 2, "AAPL", Side.BUY, 100, 10150));

        assertTrue(trades.isEmpty());
        assertEquals(10150L, engine.bestBid());
        assertEquals(10200L, engine.bestAsk());
    }

    @Test
    void buyCrossesBestAskTradesAtRestingPrice() {
        MatchingEngine engine = new MatchingEngine();
        engine.submit(new Order(1, 1, "AAPL", Side.SELL, 100, 10100));   // order 1: ask 100 @ 101.00
        engine.submit(new Order(2, 2, "AAPL", Side.SELL, 100, 10150));   // order 2: ask 100 @ 101.50

        List<Trade> trades = engine.submit(new Order(3, 1, "AAPL", Side.BUY, 100, 10150));   // order 3: buy

        assertEquals(1, trades.size());
        assertEquals(new Trade(3, 1, "AAPL", 100, 10100), trades.getFirst());   // buy 3 matched sell 1
        assertNull(engine.bestBid());
        assertEquals(10150L, engine.bestAsk());
    }

    @Test
    void buyPartiallyFillsRestingAskAndAskKeepsRemainder() {
        MatchingEngine engine = new MatchingEngine();
        engine.submit(new Order(1, 1, "AAPL", Side.SELL, 80, 10100));    // order 1: ask 80 @ 101.00

        List<Trade> trades = engine.submit(new Order(2, 2, "AAPL", Side.BUY, 50, 10100));   // order 2: buy

        assertEquals(1, trades.size());
        assertEquals(new Trade(2, 1, "AAPL", 50, 10100), trades.getFirst());    // buy 2 matched sell 1
        assertNull(engine.bestBid());
        assertEquals(10100L, engine.bestAsk());
    }

    @Test
    void sellSweepsTwoBidLevelsAndPartiallyFillsSecond() {
        MatchingEngine engine = new MatchingEngine();
        engine.submit(new Order(1, 1, "AAPL", Side.BUY, 200, 10100));    // order 1: bid 200 @ 101.00
        engine.submit(new Order(2, 2, "AAPL", Side.BUY, 100, 10050));    // order 2: bid 100 @ 100.50

        List<Trade> trades = engine.submit(new Order(3, 1, "AAPL", Side.SELL, 250, 10050));   // order 3: sell

        assertEquals(2, trades.size());
        assertEquals(new Trade(1, 3, "AAPL", 200, 10100), trades.get(0));       // buy 1 matched sell 3
        assertEquals(new Trade(2, 3, "AAPL", 50, 10050), trades.get(1));        // buy 2 matched sell 3
        assertEquals(10050L, engine.bestBid());
        assertNull(engine.bestAsk());
    }

    @Test
    void sellBelowAllBidsTradesAtEachRestingPrice() {
        MatchingEngine engine = new MatchingEngine();
        engine.submit(new Order(1, 1, "AAPL", Side.BUY, 100, 10100));    // order 1: bid 100 @ 101.00
        engine.submit(new Order(2, 2, "AAPL", Side.BUY, 500, 10050));    // order 2: bid 500 @ 100.50

        List<Trade> trades = engine.submit(new Order(3, 1, "AAPL", Side.SELL, 300, 10000));   // order 3: sell

        assertEquals(2, trades.size());
        assertEquals(new Trade(1, 3, "AAPL", 100, 10100), trades.getFirst());   // buy 1 matched sell 3
        assertEquals(new Trade(2, 3, "AAPL", 200, 10050), trades.get(1));       // buy 2 matched sell 3
        assertEquals(10050L, engine.bestBid());
        assertNull(engine.bestAsk());
    }

    @Test
    void incomingSellRecordsRestingBuyAsBuyer() {
        MatchingEngine engine = new MatchingEngine();
        engine.submit(new Order(1, 1, "AAPL", Side.BUY, 10, 10000));     // order 1 rests: the buyer

        List<Trade> trades = engine.submit(new Order(2, 2, "AAPL", Side.SELL, 10, 10000));   // order 2: the seller

        assertEquals(1L, trades.getFirst().buyerOrderId());
        assertEquals(2L, trades.getFirst().sellerOrderId());
    }

    @Test
    void incomingBuyRecordsRestingSellAsSeller() {
        MatchingEngine engine = new MatchingEngine();
        engine.submit(new Order(1, 1, "AAPL", Side.SELL, 10, 10000));    // order 1 rests: the seller

        List<Trade> trades = engine.submit(new Order(2, 2, "AAPL", Side.BUY, 10, 10000));    // order 2: the buyer

        assertEquals(2L, trades.getFirst().buyerOrderId());
        assertEquals(1L, trades.getFirst().sellerOrderId());
    }
}