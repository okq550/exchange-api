package org.okq550.exchange_api.domain;

public record Order(long id, long accountId, String symbol, Side side, int quantity, long priceInCents) {

    public Order {
        if (priceInCents <= 0) {
            throw new IllegalArgumentException("Price in cents must be positive");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be blank");
        }
    }

    // Rebuilds an order from that text. The compact constructor's checks run again here.
    public static Order fromWire(String text) {
        String[] p = text.split("\\|");
        return new Order(
                Long.parseLong(p[0]),      // id
                Long.parseLong(p[1]),      // accountId
                p[2],                      // symbol
                Side.valueOf(p[3]),        // side
                Integer.parseInt(p[4]),    // quantity
                Long.parseLong(p[5]));     // priceInCents
    }

    // Turns the order into one line of text for Kafka, e.g. "7|1|AAPL|BUY|100|10100"
    public String toWire() {
        return id + "|" + accountId + "|" + symbol + "|" + side + "|" + quantity + "|" + priceInCents;
    }
}