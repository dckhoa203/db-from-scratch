package dbformscratch.step4.service;

import dbformscratch.step4.database.MiniDatabase;
import dbformscratch.step4.model.Account;
import dbformscratch.step4.transaction.TransactionContext;
import dbformscratch.step4.transaction.TransactionFailedException;
import dbformscratch.step4.transaction.TransactionManager;

public class TransferService {

    private final MiniDatabase database;

    private final TransactionManager transactionManager;

    public TransferService(MiniDatabase database,
                           TransactionManager transactionManager) {
        this.database = database;
        this.transactionManager = transactionManager;
    }

    public TransactionContext transfer(long fromId, long toId, long amount) {
        return transfer(fromId, toId, amount, false);
    }

    public TransactionContext transferFail(long fromId, long toId, long amount) {
        return transfer(fromId, toId, amount, true);
    }

    private TransactionContext transfer(
            long fromId,
            long toId,
            long amount,
            boolean failAfterDebit
    ) {

        TransactionContext transaction = transactionManager.begin();

        try {

            Account from = database.select(fromId);

            Account updatedFrom = new Account(
                    from.id(),
                    from.accountNumber(),
                    from.balance() - amount
            );

            database.update(transaction, updatedFrom);

            if (failAfterDebit) {
                throw new RuntimeException("Failure after debit");
            }

            Account to = database.select(toId);

            Account updatedTo = new Account(
                    to.id(),
                    to.accountNumber(),
                    to.balance() + amount
            );

            database.update(transaction, updatedTo);

            transactionManager.commit(transaction);

            return transaction;

        } catch (RuntimeException exception) {

            transaction.record(
                    "TX-" + transaction.getTransactionId()
                            + " FAILURE: " + exception.getMessage()
            );
            transactionManager.rollback(transaction);

            throw new TransactionFailedException(transaction, exception);
        }
    }
}
