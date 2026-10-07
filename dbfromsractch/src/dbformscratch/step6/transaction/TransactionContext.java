package dbformscratch.step6.transaction;

import dbformscratch.step6.lock.LockMode;
import dbformscratch.step6.model.Account;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class TransactionContext {

    private final long transactionId;

    private TransactionState state;

    private final Map<Long, Account> beforeImages = new LinkedHashMap<>();

    private final Set<Long> writeSet = new LinkedHashSet<>();

    private final Map<Long, LockMode> heldLocks = new LinkedHashMap<>();

    public TransactionContext(long transactionId) {
        this.transactionId = transactionId;
        this.state = TransactionState.ACTIVE;
    }

    public long getTransactionId() {
        return transactionId;
    }

    public TransactionState getState() {
        return state;
    }

    public Map<Long, Account> getBeforeImages() {
        return beforeImages;
    }

    public Set<Long> getWriteSet() {
        return writeSet;
    }

    public void recordBeforeImage(long rowId, Account account) {
        beforeImages.putIfAbsent(rowId, account);
    }

    public void recordWrite(long rowId) {
        writeSet.add(rowId);
    }

    public void markCommitted() {
        this.state = TransactionState.COMMITTED;
    }

    public void markRolledBack() {
        this.state = TransactionState.ROLLED_BACK;
    }

    public void ensureActive() {
        if (state != TransactionState.ACTIVE) {
            throw new IllegalStateException("Transaction is not active: " + transactionId);
        }
    }

    public Map<Long, LockMode> getHeldLocks() {
        return heldLocks;
    }

    public void recordLock(long rowId, LockMode mode) {

        LockMode current = heldLocks.get(rowId);

        if (current == LockMode.EXCLUSIVE) {
            return;
        }

        heldLocks.put(rowId, mode);
    }

    public void clearHeldLocks() {
        heldLocks.clear();
    }
}
