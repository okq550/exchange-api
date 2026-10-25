package org.okq550.exchange_api;

import jakarta.annotation.PreDestroy;
import org.okq550.exchange_api.domain.Order;
import org.okq550.exchange_api.domain.Side;
import org.okq550.exchange_api.domain.Trade;
import org.okq550.exchange_api.exchange.Exchange;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ExchangeService {
    private final AccountService accountService;
    private final Exchange exchange = new Exchange();
    private final AtomicLong nextId = new AtomicLong();
    private final ConcurrentHashMap<Long, Order> orders = new ConcurrentHashMap<>();

    public ExchangeService(AccountService accountService) {
        this.accountService = accountService;
        exchange.start();
    }

    public PlaceResult place(long accountId, String symbol, Side side, int quantity, long priceInCents)
            throws InterruptedException, ExecutionException {
        // 1. Reserve cash or shares (locked and saved inside AccountService)
        boolean reserved;
        if (side == Side.BUY) {
            reserved = accountService.tryReserveCash(accountId, (long) quantity * priceInCents);
        } else {
            reserved = accountService.tryReserveShares(accountId, quantity);
        }
        if (!reserved) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient funds or shares");
        }

        // 2. Build the order and remember it for settlement
        Order order = new Order(nextId.incrementAndGet(), accountId, symbol, side, quantity, priceInCents);
        orders.put(order.id(), order);

        // 3. Match it on the engine thread
        List<Trade> trades = exchange.submit(order).get();

        // 4. Settle each trade: both accounts in one transaction
        for (Trade t : trades) {
            Order buy = orders.get(t.buyerOrderId());
            Order sell = orders.get(t.sellerOrderId());
            accountService.settleTrade(buy.accountId(), sell.accountId(), symbol,
                    t.quantity(), t.priceInCents(), buy.priceInCents());
        }

        return new PlaceResult(order.id(), trades);
    }

    public Order cancel(long orderId) throws InterruptedException, ExecutionException {
        // 1. Remove it from the book on the engine thread; the result has the remaining quantity
        Order remaining = exchange.cancel(orderId).get();
        if (remaining == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }

        // 2. Release what the remainder was holding
        if (remaining.side() == Side.BUY) {
            accountService.releaseCash(remaining.accountId(), (long) remaining.quantity() * remaining.priceInCents());
        } else {
            accountService.releaseShares(remaining.accountId(), remaining.quantity());
        }
        return remaining;
    }

    public void subscribe(BlockingQueue<String> mailbox) {
        exchange.subscribe(mailbox);
    }

    public void unsubscribe(BlockingQueue<String> mailbox) {
        exchange.unsubscribe(mailbox);
    }

    @PreDestroy
    public void shutdown() {
        exchange.stop();
    }
}
