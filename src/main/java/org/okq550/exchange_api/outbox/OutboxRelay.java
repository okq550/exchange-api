package org.okq550.exchange_api.outbox;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxRelay {
    private final OutboxMessageRepository outboxMessageRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxMessageRepository outboxMessageRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedRate = 100) // Every 100ms after the previous once finishes
    public void publish() throws Exception {
        for (OutboxMessage message : outboxMessageRepository.findTop100ByOrderByIdAsc()) {
            kafkaTemplate.send(message.getTopic(), message.getPayload()).get();
            outboxMessageRepository.delete(message);
        }
    }
}
