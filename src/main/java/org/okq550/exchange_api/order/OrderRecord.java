package org.okq550.exchange_api.order;

import jakarta.persistence.*;
import org.okq550.exchange_api.domain.Side;

import java.time.Instant;

@Entity
public class OrderRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long accountId;
    private String symbol;

    @Enumerated(EnumType.STRING)
    private Side side;

    private int quantity;
    private long priceInCents;
    private Instant createdAt;

    protected OrderRecord() {
    }

    public OrderRecord(long accountId, String symbol, Side side, int quantity, long priceInCents) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.priceInCents = priceInCents;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public long getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public Side getSide() {
        return side;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getPriceInCents() {
        return priceInCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
