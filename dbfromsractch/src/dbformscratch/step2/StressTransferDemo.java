package dbformscratch.step2;

import dbformscratch.step2.database.MiniDatabase;
import dbformscratch.step2.model.Account;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StressTransferDemo {

    public static void main(String[] args) {

        MiniDatabase database = new MiniDatabase();

        database.insert(new Account(
                1L,
                "ACC001",
                1000L
        ));

        database.insert(new Account(
                2L,
                "ACC002",
                1000L
        ));

        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < 10_000; i++) {

            int index = i;

            executor.submit(() -> {

                if (index % 2 == 0) {
                    database.transfer(
                            1L,
                            2L,
                            1L
                    );
                } else {
                    database.transfer(
                            2L,
                            1L,
                            1L
                    );
                }
            });

            Account a = database.select(1L);
            Account b = database.select(2L);

            System.out.println("A = " + a.balance());
            System.out.println("B = " + b.balance());

            System.out.println("Total = " + (a.balance() + b.balance()));
        }
    }
}
