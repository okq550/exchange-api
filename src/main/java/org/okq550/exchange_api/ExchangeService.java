package org.okq550.exchange_api;

import jakarta.annotation.PreDestroy;
import org.okq550.exchange_api.account.Account;
import org.okq550.exchange_api.domain.Order;
import org.okq550.exchange_api.domain.Side;
import org.okq550.exchange_api.domain.Trade;
import org.okq550.exchange_api.exchange.Exchange;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
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

    public List<Trade> place(long accountId, String symbol, Side side, int quantity, long priceInCents) throws InterruptedException, ExecutionException {
        Account account = accountService.find(accountId);

        boolean reserved;
        if (side == Side.BUY) {
            reserved = account.tryReserveCash((long) quantity * priceInCents);
        } else {
            reserved = account.tryReserveShares(quantity);
        }

        if (!reserved) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient funds or shares");
        }

        Order order = new Order(nextId.incrementAndGet(), accountId, symbol, side, quantity, priceInCents);
        orders.put(order.id(), order);
        List<Trade> trades = exchange.submit(order).get();
        for (Trade t : trades) {                             // NEW: settle each trade
            Order buy = orders.get(t.buyOrderId());
            Order sell = orders.get(t.sellOrderId());
            accountService.find(buy.accountId()).settleBuy(t.quantity(), t.priceInCents(), buy.priceInCents());
            accountService.find(sell.accountId()).settleSell(t.quantity(), t.priceInCents());
        }
        return trades;
    }

    public Order cancel(long orderId) throws InterruptedException, ExecutionException {
        Order remaining = exchange.cancel(orderId).get();
        if (remaining == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }

        // 1. find the account that owns the order (remaining.accountId())
        long ownerId = remaining.accountId();
        Account owner = accountService.find(ownerId);
        // 2. if it's a BUY: release cash = remaining quantity × remaining price
        if (remaining.side() == Side.BUY) {
            owner.releaseCash(remaining.quantity() * remaining.priceInCents());
        } else { //    if it's a SELL: release shares = remaining quantity
            owner.releaseShares(remaining.quantity());
        }
        return remaining;
    }

    @PreDestroy
    public void shutdown() {
        exchange.stop();
    }
}
