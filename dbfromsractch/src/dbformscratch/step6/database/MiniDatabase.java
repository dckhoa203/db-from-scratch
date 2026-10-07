package dbformscratch.step6.database;

import dbformscratch.step6.transaction.TransactionContext;
import dbformscratch.step6.model.Account;
import dbformscratch.step6.lock.LockManager;
import dbformscratch.step6.trace.TransactionTrace;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MiniDatabase {

    private final Map<Long, Account> accounts = new ConcurrentHashMap<>();

    private final TransactionTrace trace;

    private final LockManager lockManager;

    public MiniDatabase(TransactionTrace trace) {
        this.trace = trace;
        this.lockManager = new LockManager(trace);
    }

    public void insert(Account account) {
        accounts.put(account.id(), account);
    }

    public Account select(TransactionContext transaction, long id) {

        transaction.ensureActive();

        lockManager.acquireShared(transaction, id);

        Account account = accounts.get(id);
        trace.event(transaction.getTransactionId(), "READ A" + id + " = " + account.balance());
        return account;
    }

    public void update(TransactionContext transaction, Account account) {

        transaction.ensureActive();

        long rowId = account.id();

        lockManager.acquireExclusive(transaction, rowId);

        Account current = accounts.get(rowId);

        transaction.recordBeforeImage(rowId, current);

        transaction.recordWrite(rowId);

        accounts.put(rowId, account);
        trace.event(transaction.getTransactionId(), "UPDATE A" + rowId + ": "
                + current.balance() + " → " + account.balance());
    }

    public void restore(TransactionContext transaction) {
        transaction.getBeforeImages().forEach((rowId, account) -> {
            accounts.put(rowId, account);
            trace.event(transaction.getTransactionId(), "RESTORE A" + rowId + " = " + account.balance());
        });
    }

    public LockManager getLockManager() {
        return lockManager;
    }

    public TransactionTrace getTrace() {
        return trace;
    }
}
