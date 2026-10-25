package org.okq550.exchange_api;

import org.okq550.exchange_api.account.Account;
import org.okq550.exchange_api.account.AccountRepository;
import org.okq550.exchange_api.trade.TradeRecord;
import org.okq550.exchange_api.trade.TradeRecordRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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

    public AccountService(AccountRepository accountRepository, TradeRecordRepository tradeRecordRepository) {
        this.accountRepository = accountRepository;
        this.tradeRecordRepository = tradeRecordRepository;
    }

    @Transactional
    public Account.AccountView create(long initialCashInCents, long initialShares) {
        return accountRepository.save(new Account(initialCashInCents, initialShares)).view();
    }

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
    public void settleTrade(long buyAccountId, long sellAccountId, String symbol,
                            int quantity, long priceInCents, long limitInCents) {
        // Always lock the lower id first, so two settlements can never wait on each other forever.
        lock(Math.min(buyAccountId, sellAccountId));
        lock(Math.max(buyAccountId, sellAccountId));

        lock(buyAccountId).settleBuy(quantity, priceInCents, limitInCents);
        lock(sellAccountId).settleSell(quantity, priceInCents);
        tradeRecordRepository.save(new TradeRecord(buyAccountId, sellAccountId, symbol, quantity, priceInCents, Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<TradeRecord> trades(long accountId) {
        return tradeRecordRepository.findByBuyAccountIdOrSellAccountIdOrderByIdDesc(accountId, accountId);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void releaseReservationsAfterRestart() {
        accountRepository.releaseAllReservations();
    }

    // Loads the account and locks its row until the transaction ends.
    private Account lock(long accountId) {
        return accountRepository.findByIdForUpdate(accountId).orElseThrow(() -> notFound(accountId));
    }

    private ResponseStatusException notFound(long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No account " + id);
    }
}