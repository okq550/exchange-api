package org.okq550.exchange_api.messaging;

import org.okq550.exchange_api.marketdata.MarketDataBroadcaster;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MarketDataListener {

    private final MarketDataBroadcaster broadcaster;

    public MarketDataListener(MarketDataBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @KafkaListener(
            topics = "market.data",
            groupId = "exchange-api-market-data-${HOSTNAME:local}",
            properties = "auto.offset.reset=latest")
    public void onMarketData(String message) {
        broadcaster.publish(message);
    }
}