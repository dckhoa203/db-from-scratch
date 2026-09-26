package dbformscratch.step4;

import dbformscratch.step4.database.MiniDatabase;
import dbformscratch.step4.service.TransferService;
import dbformscratch.step4.transaction.TransactionContext;
import dbformscratch.step4.transaction.TransactionManager;

public class AtomicityTransactionSuccessDemo {

    public static void main(String[] args) {

        MiniDatabase database = TransactionDemoSupport.databaseWithTwoAccounts();

        TransactionManager transactionManager = new TransactionManager(database);

        TransferService transferService = new TransferService(database, transactionManager);

        TransactionContext transaction = transferService.transfer(1L, 2L, 100L);

        System.out.println("Transfer completed successfully");
        TransactionDemoSupport.printTrace(transaction);
        TransactionDemoSupport.printFinalState(database);
    }
}
