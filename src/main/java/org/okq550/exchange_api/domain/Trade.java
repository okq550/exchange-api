package org.okq550.exchange_api.domain;

public record Trade(long buyerOrderId, long sellerOrderId, String symbol, int quantity, long priceInCents) {
    public Trade {
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be blank");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        if (priceInCents <= 0) {
            throw new IllegalArgumentException("Price in cents must be positive");
        }
    }

    public Long buyOrderId() {
        return buyerOrderId;
    }

    public Long sellOrderId() {
        return sellerOrderId;
    }
}
