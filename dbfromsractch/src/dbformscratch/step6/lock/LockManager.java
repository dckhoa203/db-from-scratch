package dbformscratch.step6.lock;

import dbformscratch.step6.transaction.TransactionContext;
import dbformscratch.step6.trace.TransactionTrace;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LockManager {

    private final ConcurrentHashMap<Long, RowLock> locks = new ConcurrentHashMap<>();

    private final TransactionTrace trace;

    public LockManager(TransactionTrace trace) {
        this.trace = trace;
    }

    public void acquireShared(TransactionContext transaction, long rowId) {

        RowLock lock = getOrCreate(rowId);

        trace.event(transaction.getTransactionId(), "REQUEST S(" + rowId + ")");
        lock.acquireShared(transaction.getTransactionId());

        transaction.recordLock(rowId, LockMode.SHARED);
    }

    public void acquireExclusive(TransactionContext transaction, long rowId) {

        RowLock lock = getOrCreate(rowId);

        LockMode currentMode = transaction.getHeldLocks().get(rowId);
        String request = currentMode == LockMode.SHARED
                ? "REQUEST X(" + rowId + ") — upgrade S → X"
                : "REQUEST X(" + rowId + ")";
        trace.event(transaction.getTransactionId(), request);
        lock.acquireExclusive(transaction.getTransactionId());

        transaction.recordLock(rowId, LockMode.EXCLUSIVE);
    }

    public void releaseAll(TransactionContext transaction) {

        long transactionId = transaction.getTransactionId();

        Map<Long, LockMode> heldLocks = new LinkedHashMap<>(transaction.getHeldLocks());

        for (Map.Entry<Long, LockMode> entry : heldLocks.entrySet()) {

            long rowId = entry.getKey();

            RowLock lock = locks.get(rowId);

            if (lock != null) {
                trace.event(transactionId, "RELEASE " + entry.getValue() + "(" + rowId + ")");
                lock.release(transactionId);
            }
        }

        transaction.clearHeldLocks();
    }

    private RowLock getOrCreate(long rowId) {
        return locks.computeIfAbsent(
                rowId,
                id -> new RowLock(id, trace));
    }
}
