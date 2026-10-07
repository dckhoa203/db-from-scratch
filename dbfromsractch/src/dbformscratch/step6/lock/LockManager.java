package dbformscratch.step6.lock;

import dbformscratch.step6.transaction.TransactionContext;

import java.util.concurrent.ConcurrentHashMap;

public class LockManager {

    private final ConcurrentHashMap<Long, RowLock> locks = new ConcurrentHashMap<>();

    public void acquireShared(TransactionContext transaction, long rowId) {

        RowLock lock = getOrCreate(rowId);

        lock.acquireShared(transaction.getTransactionId());

        transaction.recordLock(rowId, LockMode.SHARED);
    }

    public void acquireExclusive(TransactionContext transaction, long rowId) {

        RowLock lock = getOrCreate(rowId);

        lock.acquireExclusive(transaction.getTransactionId());

        transaction.recordLock(rowId, LockMode.EXCLUSIVE);
    }

    public void releaseAll(TransactionContext transaction) {

        long transactionId = transaction.getTransactionId();

        for (long rowId : transaction.getHelpLocks().keySet()) {

            RowLock lock = locks.get(rowId);

            if (lock != null) {
                lock.release(transactionId);
            }
        }
    }

    private RowLock getOrCreate(long rowId) {
        return locks.computeIfAbsent(
                rowId,
                id -> new RowLock());
    }
}
