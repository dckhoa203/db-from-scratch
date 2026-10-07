package dbformscratch.step6.lock;

import java.util.HashSet;
import java.util.Set;

public class RowLock {

    private final Set<Long> shareHolders = new HashSet<>();

    private Long exclusiveHolder;

    public synchronized void acquireShared(long transactionId) {

        while (exclusiveHolder != null
                && exclusiveHolder != transactionId) {

            waitForLock();
        }

        shareHolders.add(transactionId);
    }

    public synchronized void acquireExclusive(long transactionId) {

        while (hasOtherExclusiveHolder(transactionId)
                || hasOtherSharedHolders(transactionId)) {

            waitForLock();
        }

        shareHolders.remove(transactionId);

        exclusiveHolder = transactionId;
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

    private void waitForLock() {

        try {

            wait();

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new RuntimeException("Interrupted while waiting for lock");
        }
    }
}
