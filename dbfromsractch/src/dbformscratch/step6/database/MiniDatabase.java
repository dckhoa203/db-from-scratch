package dbformscratch.step6.database;

import dbformscratch.step6.transaction.TransactionContext;
import dbformscratch.step6.model.Account;
import dbformscratch.step6.lock.LockManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MiniDatabase {

    private final Map<Long, Account> accounts = new ConcurrentHashMap<>();

    private final LockManager lockManager = new LockManager();

    public void insert(Account account) {
        accounts.put(account.id(), account);
    }

    public Account select(TransactionContext transaction, long id) {

        transaction.ensureActive();

        lockManager.acquireShared(transaction, id);

        return accounts.get(id);
    }

    public void update(TransactionContext transaction, Account account) {

        transaction.ensureActive();

        long rowId = account.id();

        lockManager.acquireExclusive(transaction, rowId);

        Account current = accounts.get(rowId);

        transaction.recordBeforeImage(rowId, current);

        transaction.recordWrite(rowId);

        accounts.put(rowId, account);
    }

    public void restore(TransactionContext transaction) {
        accounts.putAll(transaction.getBeforeImages());
    }

    public LockManager getLockManager() {
        return lockManager;
    }
}
