package org.okq550.exchange_api.messaging;

import org.apache.kafka.common.TopicPartition;
import org.okq550.exchange_api.domain.Order;
import org.okq550.exchange_api.domain.Trade;
import org.okq550.exchange_api.exchange.Exchange;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class OrderListener implements ConsumerSeekAware {
    private final Exchange exchange;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderListener(Exchange exchange, KafkaTemplate<String, String> kafkaTemplate) {
        this.exchange = exchange;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "orders.in", groupId = "matching-engine")
    public void onCommand(String message) throws Exception {
        if (message.startsWith("PLACE|")) {
            Order order;
            try {
                order = Order.fromWire(message.substring("PLACE|".length()));
            } catch (RuntimeException e) {
                System.out.println("Skipping malformed order: " + message);
                return;
            }

            List<Trade> trades = exchange.submit(order).get();
            for (Trade t : trades) {
                kafkaTemplate.send("trades", t.symbol(), t.toWire());
            }
        } else if (message.startsWith("CANCEL|")) {
            long orderId = Long.parseLong(message.substring("CANCEL|".length()));
            Order remaining = exchange.cancel(orderId).get();
            if (remaining != null) {
                kafkaTemplate.send("cancels", remaining.symbol(), remaining.toWire());
            }
        } else {
            System.out.println("Skipping unknown command: " + message);
        }
    }

    @Override
    // Callback: Spring calls it when this consumer is given its partitions, at startup or when the group rebalances
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        // Ignore the saved offset: rebuild the book from the full history.
        callback.seekToBeginning(assignments.keySet());
    }
}