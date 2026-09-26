package dbformscratch.step4;

import dbformscratch.step4.database.MiniDatabase;
import dbformscratch.step4.service.TransferService;
import dbformscratch.step4.transaction.TransactionContext;
import dbformscratch.step4.transaction.TransactionFailedException;
import dbformscratch.step4.transaction.TransactionManager;

public class AtomicityTransactionFailDemo {

    public static void main(String[] args) {

        MiniDatabase database = TransactionDemoSupport.databaseWithTwoAccounts();

        TransactionManager transactionManager = new TransactionManager(database);

        TransferService transferService = new TransferService(database, transactionManager);

        TransactionContext transaction;

        try {
            transferService.transferFail(1L, 2L, 100L);
            throw new AssertionError("The failure experiment must abort the transaction");
        } catch (TransactionFailedException exception) {
            transaction = exception.getTransaction();
            System.out.println("Transfer failed: " + exception.getCause().getMessage());
        }

        TransactionDemoSupport.printTrace(transaction);
        TransactionDemoSupport.printFinalState(database);
    }
}
