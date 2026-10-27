package org.okq550.exchange_api.account;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long cashInCents;
    private long reservedInCents;
    private long shares;
    private long reservedShares;

    // Won't be called by the code by accident
    protected Account() {
    }   // for JPA only

    public Account(long initialCashInCents, long initialShares) {
        this.cashInCents = initialCashInCents;
        this.shares = initialShares;
    }

    // Check that enough cash is available, and if so, reserve it.
    // Returns true if reserved, false if there isn't enough.
    public boolean tryReserveCash(long amountInCents) {
        if (cashInCents - reservedInCents < amountInCents) {
            return false;
        }
        reservedInCents += amountInCents;
        return true;
    }

    public boolean tryReserveShares(long quantity) {
        if (shares - reservedShares < quantity) {
            return false;
        }
        reservedShares += quantity;
        return true;
    }

    public void releaseCash(long amountInCents) {
        reservedInCents -= amountInCents;
    }

    public void releaseShares(long quantity) {
        reservedShares -= quantity;
    }

    // Deduct the reservedInCents and the cashInCents, Then increase the quantity.
    public void settleBuy(int quantity, long priceInCents, long limitInCents) {
        reservedInCents -= quantity * limitInCents;
        cashInCents -= quantity * priceInCents;
        shares += quantity;
    }

    // Deduct the reservedShares and the shares, Then increase the cashInCents.
    public void settleSell(int quantity, long priceInCents) {
        reservedShares -= quantity;
        shares -= quantity;
        cashInCents += quantity * priceInCents;
    }

    public AccountView view() {
        return new AccountView(id, cashInCents, reservedInCents,
                cashInCents - reservedInCents, shares, reservedShares);
    }

    public record AccountView(long id, long cashInCents, long reservedInCents,
                              long availableInCents, long shares, long reservedShares) {
    }
}