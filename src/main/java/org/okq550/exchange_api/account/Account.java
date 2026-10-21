package org.okq550.exchange_api.account;

public class Account {
    private final long id;
    private long cashInCents;
    private long reservedInCents;
    private long shares;
    private long reservedShares;

    public Account(long id, long initialCashInCents, long initialShares) {
        this.id = id;
        this.cashInCents = initialCashInCents;
        this.shares = initialShares;
    }

    // Check that enough cash is available, and if so, reserve it.
    // Returns true if reserved, false if there isn't enough.
    public synchronized boolean tryReserveCash(long amountInCents) {
        if (cashInCents - reservedInCents < amountInCents) {
            return false;
        } else {
            reservedInCents += amountInCents;
            return true;
        }
    }

    public synchronized void releaseCash(long amountInCents) {
        reservedInCents -= amountInCents;
    }

    public synchronized boolean tryReserveShares(long quanity) {
        if (shares - reservedShares < quanity) {
            return false;
        } else {
            reservedShares += quanity;
            return true;
        }
    }

    public synchronized AccountView view() {
        return new AccountView(id, cashInCents, reservedInCents, cashInCents - reservedInCents, shares);
    }

    public synchronized void settleBuy(int quantity, long priceInCents, long limitInCents) {
        reservedInCents -= quantity * limitInCents;
        cashInCents -= quantity * priceInCents;
        shares += quantity;
    }

    public synchronized void settleSell(int quantity, long priceInCents) {
        reservedShares -= quantity;
        shares -= quantity;
        cashInCents = cashInCents + quantity * priceInCents;
    }

    public record AccountView(long id, long cashInCents, long reservedInCents, long availableInCents, long shares) {
    }
}
