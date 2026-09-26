package dbformscratch.step4.transaction;

import dbformscratch.step4.model.Account;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TransactionContext {

    private final long transactionId;

    private TransactionState state;

    private final Map<Long, Account> beforeImages = new LinkedHashMap<>();

    private final Set<Long> writeSet = new LinkedHashSet<>();

    private final List<String> trace = new ArrayList<>();

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

    public List<String> getTrace() {
        return List.copyOf(trace);
    }

    public boolean recordBeforeImage(long rowId, Account account) {
        return beforeImages.putIfAbsent(rowId, account) == null;
    }

    public boolean recordWrite(long rowId) {
        return writeSet.add(rowId);
    }

    public void record(String message) {
        trace.add(message);
    }

    public void markCommitted() {
        this.state = TransactionState.COMMITTED;
    }

    public void markRolledBack() {
        this.state = TransactionState.ROLLED_BACK;
    }

    public void ensureActive() {
        if (state != TransactionState.ACTIVE) {
            throw new IllegalStateException("Transaction is not active " + transactionId);
        }
    }
}
