package dbformscratch.step6.lock;

import dbformscratch.step6.trace.TransactionTrace;

import java.util.HashSet;
import java.util.Set;

public class RowLock {

    private final long rowId;

    private final TransactionTrace trace;

    private final Set<Long> shareHolders = new HashSet<>();

    private Long exclusiveHolder;

    public RowLock(long rowId, TransactionTrace trace) {
        this.rowId = rowId;
        this.trace = trace;
    }

    public synchronized void acquireShared(long transactionId) {

        boolean waited = false;
        while (exclusiveHolder != null
                && exclusiveHolder != transactionId) {

            if (!waited) {
                trace.event(transactionId, "WAIT S(" + rowId + ") — held by X(T" + exclusiveHolder + ")");
                waited = true;
            }
            waitForLock();
        }

        shareHolders.add(transactionId);
        trace.event(transactionId, "GRANTED S(" + rowId + ")");
    }

    public synchronized void acquireExclusive(long transactionId) {

        boolean waited = false;
        while (hasOtherExclusiveHolder(transactionId)
                || hasOtherSharedHolders(transactionId)) {

            if (!waited) {
                trace.event(transactionId, "WAIT X(" + rowId + ") — " + holdersExcept(transactionId));
                waited = true;
            }
            waitForLock();
        }

        shareHolders.remove(transactionId);

        exclusiveHolder = transactionId;
        trace.event(transactionId, "GRANTED X(" + rowId + ")");
    }

    public synchronized void release(long transactionId) {

        shareHolders.remove(transactionId);

        if (exclusiveHolder != null
                && exclusiveHolder == transactionId) {

            exclusiveHolder = null;
        }

        notifyAll();
    }

    private boolean hasOtherExclusiveHolder(long transactionId) {
        return exclusiveHolder != null
                && exclusiveHolder != transactionId;
    }

    private boolean hasOtherSharedHolders(long transactionId) {
        return shareHolders.stream()
                .anyMatch(holder -> holder != transactionId);
    }

    private String holdersExcept(long transactionId) {
        if (exclusiveHolder != null && exclusiveHolder != transactionId) {
            return "held by X(T" + exclusiveHolder + ")";
        }
        return "held by S" + shareHolders.stream()
                .filter(holder -> holder != transactionId)
                .map(holder -> "(T" + holder + ")")
                .toList();
    }

    private void waitForLock() {

        try {

            wait();

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new RuntimeException("Interrupted while waiting for lock");
        }
    }
}
