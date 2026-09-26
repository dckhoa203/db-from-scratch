package dbformscratch.step4;

import dbformscratch.step4.database.MiniDatabase;
import dbformscratch.step4.model.Account;
import dbformscratch.step4.transaction.TransactionContext;

final class TransactionDemoSupport {

    private TransactionDemoSupport() {
    }

    static MiniDatabase databaseWithTwoAccounts() {
        MiniDatabase database = new MiniDatabase();
        database.insert(new Account(1L, "A", 1000L));
        database.insert(new Account(2L, "B", 1000L));
        return database;
    }

    static void printTrace(TransactionContext transaction) {
        System.out.println("Transaction trace");
        transaction.getTrace().forEach(event -> System.out.println("  " + event));
    }

    static void printFinalState(MiniDatabase database) {
        Account from = database.select(1L);
        Account to = database.select(2L);
        long total = from.balance() + to.balance();

        System.out.println("Final state");
        System.out.println("  A = " + from.balance());
        System.out.println("  B = " + to.balance());
        System.out.println("  Total = " + total);
        System.out.println("  Invariant preserved = " + (total == 2000L));
    }
}
