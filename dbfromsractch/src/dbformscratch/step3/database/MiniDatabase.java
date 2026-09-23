package dbformscratch.step3.database;

import dbformscratch.step3.lock.LockManager;
import dbformscratch.step3.model.Account;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MiniDatabase {

    private final Map<Long, Account> accounts = new ConcurrentHashMap<>();

    private final LockManager lockManager = new LockManager();

    public void insert(Account account) {
        accounts.put(account.id(), account);
    }

    public Account select(long id) {
        return accounts.get(id);
    }

    public void update(Account account) {
        accounts.put(account.id(), account);
    }

    public void delete(long id) {
        accounts.remove(id);
    }

    public synchronized void depositSync(long accountId, long amount) {
        applyDeposit(accountId, amount);
    }

    public synchronized void depositSyncSlow(
            long accountId,
            long amount,
            long workMillis
    ) {
        Account current = select(accountId);
        simulateWork(workMillis);
        updateBalance(current, amount);
    }

    public void deposit(long accountId, long amount) {

        lockManager.lock(accountId);

        try {
            applyDeposit(accountId, amount);
        } finally {
            lockManager.unlock(accountId);
        }
    }

    public void depositSlow(long accountId, long amount, long workMillis) {

        lockManager.lock(accountId);

        try {
            Account current = select(accountId);
            simulateWork(workMillis);
            updateBalance(current, amount);
        } finally {
            lockManager.unlock(accountId);
        }
    }

    private void applyDeposit(long accountId, long amount) {
        Account current = select(accountId);
        updateBalance(current, amount);
    }

    private void updateBalance(Account current, long amount) {
        Account updated = new Account(
                current.id(),
                current.accountNumber(),
                current.balance() + amount
        );

        update(updated);
    }

    private void simulateWork(long workMillis) {
        try {
            Thread.sleep(workMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Deposit operation was interrupted", e);
        }
    }
}
