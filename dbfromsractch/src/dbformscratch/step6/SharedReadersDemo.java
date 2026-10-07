package dbformscratch.step6;

import dbformscratch.step6.database.MiniDatabase;
import dbformscratch.step6.model.Account;
import dbformscratch.step6.trace.TransactionTrace;
import dbformscratch.step6.transaction.TransactionContext;
import dbformscratch.step6.transaction.TransactionManager;

import java.util.concurrent.CountDownLatch;

/** Two readers hold S(A) concurrently; neither waits for the other. */
public class SharedReadersDemo {

    public static void main(String[] args) {
        System.out.println("=== Shared locks allow concurrent readers ===");
        TransactionTrace trace = new TransactionTrace();
        MiniDatabase database = new MiniDatabase(trace);
        database.insert(new Account(1L, "A", 1000L));
        TransactionManager transactions = new TransactionManager(database);
        CountDownLatch firstReaderHasLock = new CountDownLatch(1);
        CountDownLatch bothReadersHaveLock = new CountDownLatch(2);
        CountDownLatch allowCommit = new CountDownLatch(1);

        Thread t1 = new Thread(() -> {
            TransactionContext transaction = transactions.begin();
            database.select(transaction, 1L);
            firstReaderHasLock.countDown();
            bothReadersHaveLock.countDown();
            DemoSupport.await(allowCommit, "permission to commit");
            transactions.commit(transaction);
        }, "T1-reader");
        Thread t2 = new Thread(() -> {
            DemoSupport.await(firstReaderHasLock, "T1 gets S(A)");
            TransactionContext transaction = transactions.begin();
            database.select(transaction, 1L);
            bothReadersHaveLock.countDown();
            DemoSupport.await(allowCommit, "permission to commit");
            transactions.commit(transaction);
        }, "T2-reader");

        t1.start();
        t2.start();
        DemoSupport.await(bothReadersHaveLock, "both readers get S(A)");
        trace.system("Both readers hold S(1) concurrently — no WAIT event.");
        allowCommit.countDown();
        DemoSupport.join(t1);
        DemoSupport.join(t2);
    }
}
