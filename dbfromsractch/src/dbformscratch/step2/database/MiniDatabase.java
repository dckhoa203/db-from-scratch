package dbformscratch.step2.database;


import dbformscratch.step2.model.Account;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;

public class MiniDatabase {

    private final Map<Long, Account> accounts = new ConcurrentHashMap<>();

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

    public void deposit(long accountId, long amount) {

        Account current = select(accountId);

        long newBalance = current.balance() + amount;

        Account updated = new Account(
              current.id(),
              current.accountNumber(),
              newBalance
        );

        update(updated);
    }

    public void depositUnsafe(long accountId, long amount, CyclicBarrier barrier) {

        Account current = select(accountId);

        try {
            barrier.await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        long newBalance = current.balance() + amount;

        Account updated = new Account(
                current.id(),
                current.accountNumber(),
                newBalance
        );

        update(updated);
    }

    public void transfer(long fromId, long toId, long amount) {

        Account from = select(fromId);
        Account to = select(toId);

        Account updatedFrom = new Account(
                from.id(),
                from.accountNumber(),
                from.balance() - amount
        );

        Account updatedTo = new Account(
                to.id(),
                to.accountNumber(),
                to.balance() + amount
        );

        update(updatedFrom);
        update(updatedTo);
    }
}
