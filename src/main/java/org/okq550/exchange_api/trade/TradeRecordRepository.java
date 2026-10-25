package org.okq550.exchange_api.trade;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeRecordRepository extends JpaRepository<TradeRecord, Long> {
    List<TradeRecord> findByBuyAccountIdOrSellAccountIdOrderByIdDesc(long buyAccountId, long sellAccountId);
}
