package org.okq550.exchange_api.exchange;

import org.okq550.exchange_api.domain.Order;
import org.okq550.exchange_api.domain.Trade;
import org.okq550.exchange_api.engine.MatchingEngine;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;


public class Exchange {

    private final BlockingQueue<Command> inbound = new ArrayBlockingQueue<>(10_000);
    private final MatchingEngine engine = new MatchingEngine();
    private final Thread engineThread = new Thread(this::run, "engine");
    private final List<BlockingQueue<String>> subscribers = new CopyOnWriteArrayList<>();
    Logger logger = Logger.getLogger(getClass().getName());

    public void subscribe(BlockingQueue<String> mailbox) {
        subscribers.add(mailbox);
    }

    public void unsubscribe(BlockingQueue<String> mailbox) {
        subscribers.remove(mailbox);
    }

    public void publish(String message) {
        for (BlockingQueue<String> mailbox : subscribers) {
            if (!mailbox.offer(message)) {
                logger.info("Slow subscriber: message dropped");
            }
        }
    }

    public void start() {
        engineThread.start();
    }

    // Called by any client thread. Returns a receipt that the engine fills in later.
    public CompletableFuture<List<Trade>> submit(Order order) throws InterruptedException {
        CompletableFuture<List<Trade>> result = new CompletableFuture<>();
        inbound.put(new Place(order, result));
        return result;
    }

    public CompletableFuture<Order> cancel(Long orderId) throws InterruptedException {
        CompletableFuture<Order> result = new CompletableFuture<>();
        inbound.put(new Cancel(orderId, result));
        return result;
    }

    public boolean isIdle() {
        return inbound.isEmpty();
    }

    public void stop() {
        engineThread.interrupt();
    }

    // Runs only on the engine thread: the single writer.
    private void run() {
        try {
            while (true) {
                Command command = inbound.take();
                switch (command) {
                    case Place p -> {
                        List<Trade> trades = engine.submit(p.order());
                        p.result().complete(trades);

                        for (Trade trade : trades) {
                            publish("MARKET TRADE " + trade.quantity() + " " + trade.symbol() + " @ " + trade.priceInCents());
                        }
                        publish("MARKET BEST bid=" + engine.bestBid() + " ask=" + engine.bestAsk());
                    }
                    case Cancel c -> {
                        c.result().complete(engine.cancel(c.orderId()));
                    }
                }
            }
        } catch (InterruptedException e) {
            // stop() was called
            logger.warning(e.getMessage());
        }
    }

    private sealed interface Command permits Place, Cancel {
    }

    private record Place(Order order, CompletableFuture<List<Trade>> result) implements Command {
    }

    private record Cancel(Long orderId, CompletableFuture<Order> result) implements Command {
    }

    // A queued item: the order plus the box its result goes into
    private record Request(Order order, CompletableFuture<List<Trade>> result) {
    }
}