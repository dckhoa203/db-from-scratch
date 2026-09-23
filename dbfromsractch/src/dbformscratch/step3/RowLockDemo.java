package dbformscratch.step3;

import dbformscratch.step3.database.MiniDatabase;
import dbformscratch.step3.model.Account;

public class RowLockDemo {

    public static void main(String[] args) throws InterruptedException {

        MiniDatabase database = new MiniDatabase();

        database.insert(new Account(
                1L,
                "ACC001",
                1000L
        ));

        Thread t1 = new Thread(
                () -> database.deposit(1L, 100L),
                "RowLock-1"
        );

        Thread t2 = new Thread(
                () -> database.deposit(1L, 100L),
                "RowLock-2"
        );

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        Account account = database.select(1L);

        System.out.println("Mechanism = row lock");
        System.out.println("Target row = Account #1");
        System.out.println("Expected = 1200");
        System.out.println("Actual   = " + account.balance());
        System.out.println("Lost update prevented = " + (account.balance() == 1200L));
    }
}
