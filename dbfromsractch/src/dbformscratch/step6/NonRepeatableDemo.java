package dbformscratch.step6;

import dbformscratch.step6.database.MiniDatabase;
import dbformscratch.step6.model.Account;
import dbformscratch.step6.trace.TransactionTrace;
import dbformscratch.step6.transaction.TransactionContext;
import dbformscratch.step6.transaction.TransactionManager;

public class NonRepeatableDemo {

    public static void main(String[] args) {
        System.out.println("=== Non-repeatable read is prevented ===");

        TransactionTrace trace = new TransactionTrace();
        MiniDatabase database = new MiniDatabase(trace);

        database.insert(new Account(
                1L,
                "A",
                1000L
        ));

        TransactionManager txManager = new TransactionManager(database);

        TransactionContext t1 = txManager.begin();

        Account first = database.select(
                t1,
                1L
        );

        Thread writer = new Thread(() -> {
            TransactionContext t2 = txManager.begin();
            database.update(t2, new Account(1L, "A", 500L));
            txManager.commit(t2);
        }, "T2-writer");
        writer.start();

        trace.awaitEventContaining("T2 WAIT X(1)");
        Account second = database.select(t1, 1L);
        trace.system("T1 saw stable value across reads = "
                + (first.balance() == second.balance()));
        txManager.commit(t1);

        DemoSupport.join(writer);
        TransactionContext finalRead = txManager.begin();
        long finalBalance = database.select(finalRead, 1L).balance();
        txManager.commit(finalRead);
        trace.system("FINAL A1 = " + finalBalance);
    }
}
