package org.okq550.exchange_api.outbox;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.Instant;

@Entity
public class OutboxMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String topic;
    private String msgKey;
    private String payload;
    private Instant createdAt;

    protected OutboxMessage() {
    }

    public OutboxMessage(String topic, String msgKey, String payload) {
        this.topic = topic;
        this.msgKey = msgKey;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public long getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getMsgKey() {
        return msgKey;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
