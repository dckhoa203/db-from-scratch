package dbformscratch.step2;

import dbformscratch.step2.model.Account;
import dbformscratch.step2.database.MiniDatabase;

import java.util.concurrent.CyclicBarrier;

public class LostUpdateDemo {

    public static void main(String[] args) throws InterruptedException {

        MiniDatabase database = new MiniDatabase();

        database.insert(new Account(
                1L,
                "ACC001",
                1000L
        ));

        CyclicBarrier barrier = new CyclicBarrier(2);

        Thread thread1 = new Thread(() -> {

            database.depositWithBarrier(
                    1L,
                    100L,
                    barrier
            );
        }, "Thread-1");

        Thread thread2 = new Thread(() -> {

            database.depositWithBarrier(
                    1L,
                    100L,
                    barrier
            );
        }, "Thread-2");

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        Account account = database.select(1L);

        System.out.println("Expected = 1200");

        System.out.println("Actual = " + account.balance());

        System.out.println("Lost update reproduced = " + (account.balance() == 1100L));
    }
}
