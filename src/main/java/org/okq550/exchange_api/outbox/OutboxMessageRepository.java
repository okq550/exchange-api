package org.okq550.exchange_api.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {
    List<OutboxMessage> findTop100ByOrderByIdAsc();

    // True if this transaction got the relay lock; false if another pod holds it.
    @Query(value = "select pg_try_advisory_xact_lock(4242)", nativeQuery = true)
    boolean tryRelayLock();
}
