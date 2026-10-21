package org.okq550.exchange_api;

import org.okq550.exchange_api.account.Account;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AccountService {
    private final ConcurrentHashMap<Long, Account> accounts = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(0);

    public Account.AccountView create(long initialCashInCents, long initialShares) {
        long id = nextId.incrementAndGet();
        Account account = new Account(id, initialCashInCents, initialShares);
        accounts.put(id, account);
        return account.view();
    }

    public Account find(long id) {
        Account account = accounts.get(id);
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No account " + id);
        }
        return account;
    }
}