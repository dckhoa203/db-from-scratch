package dbformscratch.step2;

import dbformscratch.step2.model.Account;
import dbformscratch.step2.database.MiniDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ConcurrentDepositDemo {

    public static void main(String[] args) throws InterruptedException {

        MiniDatabase database = new MiniDatabase();

        database.insert(new Account(
                1L,
                "ACC001",
                1000L
        ));

        int numberOfDeposits = 100;
        int numberOfWorkers = 10;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfWorkers);

        for (int i = 0; i < numberOfDeposits; i++) {

            executor.submit(() -> {
                database.deposit(1L, 10L);
            });
        }

        executor.shutdown();
        boolean completed = executor.awaitTermination(
                10,
                TimeUnit.SECONDS
        );

        if (!completed) {
            throw new IllegalStateException("Deposit experiment timed out");
        }

        Account account = database.select(1L);

        System.out.println("Deposit operations = " + numberOfDeposits);
        System.out.println("Worker threads     = " + numberOfWorkers);
        System.out.println("Expected balance = 2000");
        System.out.println("Actual balance   = " + account.balance());
        System.out.println("Lost update observed = " + (account.balance() < 2000L));
    }
}
