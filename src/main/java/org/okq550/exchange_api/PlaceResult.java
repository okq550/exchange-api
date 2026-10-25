package org.okq550.exchange_api;

import org.okq550.exchange_api.domain.Trade;

import java.util.List;

public record PlaceResult(long orderId, List<Trade> trades) {
}

