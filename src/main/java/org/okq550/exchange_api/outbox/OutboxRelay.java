package org.okq550.exchange_api.outbox;

import jakarta.transaction.Transactional;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxRelay {
    private final OutboxMessageRepository outbox;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxMessageRepository outbox, KafkaTemplate<String, String> kafkaTemplate) {
        this.outbox = outbox;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 100) // Every 100ms after the previous once finishes
    @Transactional
    public void publish() throws Exception {
        if (!outbox.tryRelayLock()) {
            return;   // another pod is the active relay right now
        }
        for (OutboxMessage m : outbox.findTop100ByOrderByIdAsc()) {
            kafkaTemplate.send(m.getTopic(), m.getMsgKey(), m.getPayload()).get();
            outbox.delete(m);
        }
    }
}
