package dbformscratch.step3;

import dbformscratch.step3.database.MiniDatabase;
import dbformscratch.step3.model.Account;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class LockGranularityDemo {

    private static final long WORK_MILLIS = 500L;

    public static void main(String[] args) throws InterruptedException {

        long globalDifferentRows = runGlobalLockOnDifferentRows();
        long rowLockSameRow = runRowLockOnSameRow();
        long rowLockDifferentRows = runRowLockOnDifferentRows();

        System.out.println();
        System.out.println("Summary");
        System.out.println("Global lock, different rows = " + globalDifferentRows + " ms");
        System.out.println("Row lock, same row          = " + rowLockSameRow + " ms");
        System.out.println("Row lock, different rows    = " + rowLockDifferentRows + " ms");
        System.out.println();
        System.out.println("Expected shape: ~1000 ms, ~1000 ms, ~500 ms");
    }

    private static long runGlobalLockOnDifferentRows() throws InterruptedException {
        MiniDatabase database = databaseWithTwoAccounts();

        long elapsed = runConcurrently(
                () -> database.depositSyncSlow(1L, 100L, WORK_MILLIS),
                () -> database.depositSyncSlow(2L, 100L, WORK_MILLIS)
        );

        System.out.println("Global lock / different rows");
        printTwoAccountResult(database, elapsed);
        return elapsed;
    }

    private static long runRowLockOnSameRow() throws InterruptedException {
        MiniDatabase database = databaseWithTwoAccounts();

        long elapsed = runConcurrently(
                () -> database.depositSlow(1L, 100L, WORK_MILLIS),
                () -> database.depositSlow(1L, 100L, WORK_MILLIS)
        );

        Account account = database.select(1L);

        System.out.println();
        System.out.println("Row lock / same row");
        System.out.println("Expected balance = 1200");
        System.out.println("Actual balance   = " + account.balance());
        System.out.println("Elapsed          = " + elapsed + " ms");
        return elapsed;
    }

    private static long runRowLockOnDifferentRows() throws InterruptedException {
        MiniDatabase database = databaseWithTwoAccounts();

        long elapsed = runConcurrently(
                () -> database.depositSlow(1L, 100L, WORK_MILLIS),
                () -> database.depositSlow(2L, 100L, WORK_MILLIS)
        );

        System.out.println();
        System.out.println("Row lock / different rows");
        printTwoAccountResult(database, elapsed);
        return elapsed;
    }

    private static MiniDatabase databaseWithTwoAccounts() {
        MiniDatabase database = new MiniDatabase();
        database.insert(new Account(1L, "ACC001", 1000L));
        database.insert(new Account(2L, "ACC002", 1000L));
        return database;
    }

    private static long runConcurrently(
            Runnable firstOperation,
            Runnable secondOperation
    ) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread first = experimentThread(
                "Experiment-1",
                firstOperation,
                ready,
                start,
                failure
        );

        Thread second = experimentThread(
                "Experiment-2",
                secondOperation,
                ready,
                start,
                failure
        );

        first.start();
        second.start();

        ready.await();

        long startedAt = System.nanoTime();
        start.countDown();

        first.join();
        second.join();

        if (failure.get() != null) {
            throw new IllegalStateException("Lock experiment failed", failure.get());
        }

        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private static Thread experimentThread(
            String name,
            Runnable operation,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicReference<Throwable> failure
    ) {
        return new Thread(() -> {
            ready.countDown();

            try {
                start.await();
                operation.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failure.compareAndSet(null, e);
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            }
        }, name);
    }

    private static void printTwoAccountResult(MiniDatabase database, long elapsed) {
        Account first = database.select(1L);
        Account second = database.select(2L);

        System.out.println("Expected balances = 1100, 1100");
        System.out.println("Actual balances   = " + first.balance() + ", " + second.balance());
        System.out.println("Elapsed           = " + elapsed + " ms");
    }
}
