package dbformscratch.step4.transaction;

import dbformscratch.step4.database.MiniDatabase;

import java.util.concurrent.atomic.AtomicLong;

public class TransactionManager {

    private final AtomicLong transactionIdGenerator = new AtomicLong(0);

    private final MiniDatabase database;

    public TransactionManager(MiniDatabase database) {
        this.database = database;
    }

    public TransactionContext begin() {

        long transactionId = transactionIdGenerator.incrementAndGet();

        TransactionContext transaction = new TransactionContext(transactionId);
        transaction.record("BEGIN TX-" + transactionId);
        return transaction;
    }

    public void commit(TransactionContext transaction) {

        transaction.ensureActive();

        transaction.record("COMMIT TX-" + transaction.getTransactionId());
        transaction.markCommitted();
        transaction.record("TX-" + transaction.getTransactionId() + " STATE = COMMITTED");
    }

    public void rollback(TransactionContext transaction) {

        transaction.ensureActive();

        transaction.record("ROLLBACK TX-" + transaction.getTransactionId());
        database.restore(transaction);

        transaction.markRolledBack();
        transaction.record("TX-" + transaction.getTransactionId() + " STATE = ROLLED_BACK");
    }
}
