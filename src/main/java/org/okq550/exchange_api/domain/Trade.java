package org.okq550.exchange_api.domain;

public record Trade(long buyerOrderId, long sellerOrderId,
                    long buyAccountId, long sellAccountId, long buyLimitInCents,
                    String symbol, int quantity, long priceInCents) {

    public static Trade fromWire(String text) {
        String[] p = text.split("\\|");
        return new Trade(
                Long.parseLong(p[0]),      // buyerOrderId
                Long.parseLong(p[1]),      // sellerOrderId
                Long.parseLong(p[2]),      // buyAccountId
                Long.parseLong(p[3]),      // sellAccountId
                Long.parseLong(p[4]),      // buyLimitInCents
                p[5],                      // symbol
                Integer.parseInt(p[6]),    // quantity
                Long.parseLong(p[7]));     // priceInCents
    }

    // One line of text for Kafka, e.g. "3|1|1|2|10150|AAPL|100|10100"
    public String toWire() {
        return buyerOrderId + "|" + sellerOrderId + "|"
                + buyAccountId + "|" + sellAccountId + "|" + buyLimitInCents + "|"
                + symbol + "|" + quantity + "|" + priceInCents;
    }
}
