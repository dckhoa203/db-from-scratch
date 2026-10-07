package dbformscratch.step6;

import dbformscratch.step6.database.MiniDatabase;
import dbformscratch.step6.model.Account;
import dbformscratch.step6.trace.TransactionTrace;
import dbformscratch.step6.transaction.TransactionContext;
import dbformscratch.step6.transaction.TransactionManager;

/** A second writer waits for X(A), then writes after the first commit. */
public class WriterContentionDemo {

    public static void main(String[] args) {
        System.out.println("=== Exclusive locks serialize writers ===");
        TransactionTrace trace = new TransactionTrace();
        MiniDatabase database = new MiniDatabase(trace);
        database.insert(new Account(1L, "A", 1000L));
        TransactionManager transactions = new TransactionManager(database);

        TransactionContext t1 = transactions.begin();
        database.update(t1, new Account(1L, "A", 900L));

        Thread secondWriter = new Thread(() -> {
            TransactionContext t2 = transactions.begin();
            database.update(t2, new Account(1L, "A", 850L));
            transactions.commit(t2);
        }, "T2-writer");
        secondWriter.start();

        trace.awaitEventContaining("T2 WAIT X(1)");
        transactions.commit(t1);
        DemoSupport.join(secondWriter);

        TransactionContext finalRead = transactions.begin();
        long finalBalance = database.select(finalRead, 1L).balance();
        transactions.commit(finalRead);
        trace.system("FINAL A1 = " + finalBalance);
    }
}
