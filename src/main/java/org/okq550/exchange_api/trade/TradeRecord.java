package org.okq550.exchange_api.trade;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.Instant;

@Entity
public class TradeRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long buyAccountId;
    private long sellAccountId;
    private String symbol;
    private int quantity;
    private long priceInCents;
    private Instant executedAt;

    protected TradeRecord() {
    }    // for JPA only

    public TradeRecord(long buyAccountId, long sellAccountId, String symbol, int quantity, long priceInCents, Instant executedAt) {
        this.buyAccountId = buyAccountId;
        this.sellAccountId = sellAccountId;
        this.symbol = symbol;
        this.quantity = quantity;
        this.priceInCents = priceInCents;
        this.executedAt = executedAt;
    }

    public Long getId() {
        return id;
    }

    public long getBuyAccountId() {
        return buyAccountId;
    }

    public long getSellAccountId() {
        return sellAccountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getPriceInCents() {
        return priceInCents;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }
}
