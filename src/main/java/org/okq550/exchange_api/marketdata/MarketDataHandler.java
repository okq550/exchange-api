package org.okq550.exchange_api.marketdata;

import org.okq550.exchange_api.ExchangeService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MarketDataHandler extends TextWebSocketHandler {

    private final ExchangeService exchangeService;
    private final ConcurrentHashMap<String, Subscriber> subscribers = new ConcurrentHashMap<>();

    public MarketDataHandler(ExchangeService exchangeService) {
        this.exchangeService = exchangeService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        BlockingQueue<String> mailbox = new ArrayBlockingQueue<>(1000);
        Thread courier = Thread.startVirtualThread(() -> {
            try {
                while (true) {
                    String message = mailbox.take();
                    session.sendMessage(new TextMessage(message));
                }
            } catch (InterruptedException | IOException e) {
                // browser disconnected, or we were stopped
            }
        });
        exchangeService.subscribe(mailbox);
        subscribers.put(session.getId(), new Subscriber(mailbox, courier));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Subscriber subscriber = subscribers.remove(session.getId());
        if (subscriber != null) {
            exchangeService.unsubscribe(subscriber.mailbox());
            subscriber.courier().interrupt();
        }
    }

    private record Subscriber(BlockingQueue<String> mailbox, Thread courier) {
    }
}