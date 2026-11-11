package org.okq550.exchange_api.marketdata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class MarketDataBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(MarketDataBroadcaster.class);

    private final List<BlockingQueue<String>> mailboxes = new CopyOnWriteArrayList<>();

    public void subscribe(BlockingQueue<String> mailbox) {
        mailboxes.add(mailbox);
    }

    public void unsubscribe(BlockingQueue<String> mailbox) {
        mailboxes.remove(mailbox);
    }

    // Never waits: a full mailbox (slow browser) just misses this message.
    public void publish(String message) {
        for (BlockingQueue<String> mailbox : mailboxes) {
            if (!mailbox.offer(message)) {
                log.warn("Slow subscriber: message dropped");
            }
        }
    }
}