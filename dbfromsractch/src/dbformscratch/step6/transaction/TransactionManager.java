package dbformscratch.step6.transaction;

import dbformscratch.step6.database.MiniDatabase;
import dbformscratch.step6.lock.LockManager;

import java.util.concurrent.atomic.AtomicLong;

public class TransactionManager {

    private final AtomicLong transactionIdGenerator = new AtomicLong();

    private final MiniDatabase database;

    private final LockManager lockManager;

    public TransactionManager(MiniDatabase database) {
        this.database = database;
        this.lockManager = database.getLockManager();
    }

    public TransactionContext begin() {
        TransactionContext transaction = new TransactionContext(transactionIdGenerator.incrementAndGet());
        database.getTrace().event(transaction.getTransactionId(), "BEGIN");
        return transaction;
    }

    public void commit(TransactionContext transaction) {

        transaction.ensureActive();

        database.getTrace().event(transaction.getTransactionId(), "COMMIT");
        transaction.markCommitted();

        lockManager.releaseAll(transaction);
    }

    public void rollback(TransactionContext transaction) {

        transaction.ensureActive();

        try {

            database.getTrace().event(transaction.getTransactionId(), "ROLLBACK");

            database.restore(transaction);

            transaction.markRolledBack();

        } finally {

            lockManager.releaseAll(transaction);
        }
    }
}
