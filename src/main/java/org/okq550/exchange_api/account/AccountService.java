package org.okq550.exchange_api.account;

import org.okq550.exchange_api.domain.Order;
import org.okq550.exchange_api.domain.Side;
import org.okq550.exchange_api.trade.TradeRecord;
import org.okq550.exchange_api.trade.TradeRecordRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final TradeRecordRepository tradeRecordRepository;
    private final ProcessedEventRepository processedEventRepository;

    public AccountService(AccountRepository accountRepository, TradeRecordRepository tradeRecordRepository, ProcessedEventRepository processedEventRepository) {
        this.accountRepository = accountRepository;
        this.tradeRecordRepository = tradeRecordRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    public Account.AccountView create(long initialCashInCents, long initialShares) {
        return accountRepository.save(new Account(initialCashInCents, initialShares)).view();
    }

    // TODO: Why readOnly?
    @Transactional(readOnly = true)
    public Account.AccountView view(long id) {
        return accountRepository.findById(id).orElseThrow(() -> notFound(id)).view();
    }

    @Transactional
    public boolean tryReserveCash(long accountId, long amountInCents) {
        return lock(accountId).tryReserveCash(amountInCents);
    }

    @Transactional
    public boolean tryReserveShares(long accountId, long quantity) {
        return lock(accountId).tryReserveShares(quantity);
    }

    @Transactional
    public void releaseCash(long accountId, long amountInCents) {
        lock(accountId).releaseCash(amountInCents);
    }

    @Transactional
    public void releaseShares(long accountId, long quantity) {
        lock(accountId).releaseShares(quantity);
    }

    // Both sides of a trade in ONE transaction: both updates are saved, or neither is.
    @Transactional
    public void settleTrade(long buyerOrderId, long sellerOrderId, long buyAccountId, long sellAccountId,
                            String symbol, int quantity, long priceInCents, long limitInCents) {
        if (!firstTime("trade-" + buyerOrderId + "-" + sellerOrderId)) {
            return;   // a redelivered duplicate: already settled
        }
        // Always lock the lower id first, so two settlements can never wait on each other forever.
        lock(Math.min(buyAccountId, sellAccountId));
        lock(Math.max(buyAccountId, sellAccountId));
        // TODO:  Why locking the accounts alone above then locking again for settleBuy and settleSell?
        lock(buyAccountId).settleBuy(quantity, priceInCents, limitInCents);
        lock(sellAccountId).settleSell(quantity, priceInCents);
        tradeRecordRepository.save(new TradeRecord(buyAccountId, sellAccountId, symbol, quantity, priceInCents, Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<TradeRecord> trades(long accountId) {
        return tradeRecordRepository.findByBuyAccountIdOrSellAccountIdOrderByIdDesc(accountId, accountId);
    }

    // When the app is in ready state, release all pending reservations.
//    @EventListener(ApplicationReadyEvent.class)
//    @Transactional
//    public void releaseReservationsAfterRestart() {
//        accountRepository.releaseAllReservations();
//    }

    // Loads the account and locks its row until the transaction ends.
    private Account lock(long accountId) {
        return accountRepository.findByIdForUpdate(accountId).orElseThrow(() -> notFound(accountId));
    }

    private ResponseStatusException notFound(long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No account " + id);
    }

    // Records the event and returns true the first time; returns false if it was already processed.
    private boolean firstTime(String eventId) {
        if (processedEventRepository.existsById(eventId)) {
            return false;
        }
        processedEventRepository.save(new ProcessedEvent(eventId));
        return true;
    }

    // Releases what a cancelled order was still holding. Safe to call twice for the same order.
    @Transactional
    public void releaseCancelled(Order remaining) {
        if (!firstTime("cancel-" + remaining.id())) {
            return;   // a redelivered duplicate: already released
        }

        Account account = lock(remaining.accountId());
        if (remaining.side() == Side.BUY) {
            account.releaseCash((long) remaining.quantity() * remaining.priceInCents());
        } else {
            account.releaseShares(remaining.quantity());
        }
    }
}
