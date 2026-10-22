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
        // Scenario: Sell 100 @ 10200 rests; buy 100 @ 10150 arrives
        // Expectation: No trades. Best bid 10150, best ask 10200
        // Guarantees: Orders whose prices don’t cross never trade, and both rest
        // Description: The buyer won’t pay what the seller asks, so both wait in the book
        MatchingEngine engine = new MatchingEngine();
        engine.submit(new Order(1, 1, "AAPL", Side.SELL, 100, 10200));

        List<Trade> trades = engine.submit(new Order(2, 2, "AAPL", Side.BUY, 100, 10150));

        assertTrue(trades.isEmpty());
        assertEquals(10150L, engine.bestBid());
        assertEquals(10200L, engine.bestAsk());
    }

    @Test
    void buyCrossesBestAskTradesAtRestingPrice() {
        // Scenario: Asks 100 @ 10100 (order 1) and 100 @ 10150 (order 2); buy 100 @ 10150 (order 3)
        // Expectation: 1 trade: buy 3, sell 1, 100 @ 10100. Best bid none, best ask 10150
        // Guarantees: An incoming buy takes the cheapest ask first, at the resting price, and a fully filled order leaves no trace
        // Description: The buyer gets the cheaper seller and pays that seller’s price, not their own limit
        MatchingEngine engine = new MatchingEngine();
        engine.submit(new Order(1, 1, "AAPL", Side.SELL, 100, 10100));   // order 1: ask 100 @ 101.00
        engine.submit(new Order(2, 2, "AAPL", Side.SELL, 100, 10150));   // order 2: ask 100 @ 101.50

        assertEquals(10100, engine.bestAsk());

        List<Trade> trades = engine.submit(new Order(3, 1, "AAPL", Side.BUY, 100, 10150));   // order 3: buy

        assertEquals(1, trades.size());
        assertEquals(new Trade(3, 1, "AAPL", 100, 10100), trades.getFirst());   // buy 3 matched sell 1
        assertNull(engine.bestBid());
        assertEquals(10150L, engine.bestAsk());
    }

    @Test
    void buyPartiallyFillsRestingAskAndAskKeepsRemainder() {
        // Scenario: Ask 80 @ 10100 (order 1); buy 50 @ 10100 (order 2)
        // Expectation: 1 trade: buy 2, sell 1, 50 @ 10100. Best bid none, best ask 10100 with 30 left
        // Guarantees: A partially filled resting order keeps its remainder and its place in line
        // Description: The seller sold only part of their shares, and the rest is still on offer
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
        // Scenario: Bids 200 @ 10100 (order 1) and 100 @ 10050 (order 2); sell 250 @ 10050 (order 3)
        // Expectation: 2 trades: buy 1, sell 3, 200 @ 10100; buy 2, sell 3, 50 @ 10050. Best bid 10050 with 50 left, best ask none
        // Guarantees: An incoming order works through several price levels, the best first, until it’s filled
        // Description: The seller takes all the best bid, then part of the next one
        MatchingEngine engine = new MatchingEngine();
        engine.submit(new Order(1, 1, "AAPL", Side.BUY, 200, 10100));    // order 1: bid 200 @ 101.00
        engine.submit(new Order(2, 2, "AAPL", Side.BUY, 100, 10050));    // order 2: bid 100 @ 100.50

        List<Trade> trades = engine.submit(new Order(3, 3, "AAPL", Side.SELL, 250, 10050));   // order 3: sell

        assertEquals(2, trades.size());
        assertEquals(new Trade(1, 3, "AAPL", 200, 10100), trades.get(0));       // buy 1 matched sell 3
        assertEquals(new Trade(2, 3, "AAPL", 50, 10050), trades.get(1));        // buy 2 matched sell 3
        assertEquals(10050L, engine.bestBid());
        assertNull(engine.bestAsk());
    }

    @Test
    void sellBelowAllBidsTradesAtEachRestingPrice() {
        // Scenario: Bids 100 @ 10100 (order 1) and 500 @ 10050 (order 2); sell 300 @ 10000 (order 3)
        // Expectation: 2 trades: buy 1, sell 3, 100 @ 10100; buy 2, sell 3, 200 @ 10050. Best bid 10050 with 300 left, best ask none
        // Guarantees: Each trade uses the resting bid’s own price, never the incoming seller’s lower limit
        // Description: The seller would accept 100.00 but gets paid more, because the buyers offered more
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
        // Scenario: Buy 10 @ 10000 rests (order 1); sell 10 @ 10000 arrives (order 2)
        // Expectation: Buyer order 1, seller order 2
        // Guarantees: The trade names the correct buyer and seller when the seller arrives second
        // Description: Settlement charges and credits the right accounts
        MatchingEngine engine = new MatchingEngine();
        engine.submit(new Order(1, 1, "AAPL", Side.BUY, 10, 10000));     // order 1 rests: the buyer

        List<Trade> trades = engine.submit(new Order(2, 2, "AAPL", Side.SELL, 10, 10000));   // order 2: the seller

        assertEquals(1L, trades.getFirst().buyerOrderId());
        assertEquals(2L, trades.getFirst().sellerOrderId());
    }

    @Test
    void incomingBuyRecordsRestingSellAsSeller() {
        // Scenario: Sell 10 @ 10000 rests (order 1); buy 10 @ 10000 arrives (order 2)
        // Expectation: Buyer order 2, seller order 1
        // Guarantees: The same, when the buyer arrives second
        // Description: Protects against swapping buyer and seller ids
        MatchingEngine engine = new MatchingEngine();
        engine.submit(new Order(1, 1, "AAPL", Side.SELL, 10, 10000));    // order 1 rests: the seller

        List<Trade> trades = engine.submit(new Order(2, 2, "AAPL", Side.BUY, 10, 10000));    // order 2: the buyer

        assertEquals(2L, trades.getFirst().buyerOrderId());
        assertEquals(1L, trades.getFirst().sellerOrderId());
    }
}