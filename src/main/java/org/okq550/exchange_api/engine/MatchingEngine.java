package org.okq550.exchange_api.engine;

import org.okq550.exchange_api.book.OrderBook;
import org.okq550.exchange_api.domain.Order;
import org.okq550.exchange_api.domain.Side;
import org.okq550.exchange_api.domain.Trade;

import java.util.ArrayList;
import java.util.List;

public class MatchingEngine {
    private final OrderBook book = new OrderBook();

    // Stub: will match orders later. For now, it returns no trade.
    public List<Trade> submit(Order order) {
        List<Trade> trades = new ArrayList<>();

        int remaining = order.quantity();
        Side otherSide = order.side().opposite();

        while (remaining > 0) {
            // Determine the trade price
            Order best = book.peekBest(otherSide);

            // Determine weather to trade or not
            if (best == null || !crosses(order, best)) {
                break;
            }

            int tradeQty = Math.min(remaining, best.quantity());
            // best.priceInCents() is the resting order price.

            long buyId = (order.side() == Side.BUY) ? order.id() : best.id();
            long sellId = (order.side() == Side.BUY) ? best.id() : order.id();

            trades.add(new Trade(buyId, sellId, order.symbol(), tradeQty, best.priceInCents()));
            remaining = remaining - tradeQty;
            if (tradeQty == best.quantity()) {
                book.cancel(best.id());
            } else {
                book.reduceBest(order.side().opposite(), best.quantity() - tradeQty);
            }
        }

        if (remaining > 0) {
            book.add(new Order(order.id(), order.accountId(), order.symbol(), order.side(), remaining, order.priceInCents()));
        }

        return trades;
    }

    public Long bestBid() {
        return this.book.bestBid();
    }

    public Long bestAsk() {
        return this.book.bestAsk();
    }

    public Order cancel(long orderId) {
        return book.cancel(orderId);
    }

    // Do the incoming order's price and the resting order's price cross?
    private boolean crosses(Order incoming, Order resting) {
        // incoming BUY:  crosses if the resting ask's price is at or BELOW the buy's price
        if (incoming.side() == Side.BUY) {
            return resting.priceInCents() <= incoming.priceInCents();
        } else {
            // incoming SELL: crosses if the resting bid's price is at or ABOVE the sell's price
            return resting.priceInCents() >= incoming.priceInCents();
        }
    }
}
