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

        int numberOfThreads = 100;

        ExecutorService executor = Executors.newFixedThreadPool(10);

        for (int i = 0; i < numberOfThreads; i++) {

            executor.submit(() -> {
                database.deposit(1L, 10L);
            });
        }

        executor.shutdown();
        executor.awaitTermination(
                10,
                TimeUnit.SECONDS
        );

        Account account = database.select(1L);

        System.out.println("Expected balance = 2000");

        System.out.println("Actual balance = " + account.balance());
    }
}
