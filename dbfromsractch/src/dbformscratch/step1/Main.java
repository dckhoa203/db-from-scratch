package dbformscratch.step1;

import dbformscratch.step1.database.MiniDatabase;
import dbformscratch.step1.model.Account;

public class Main {

    public static void main(String[] args) {

        MiniDatabase database = new MiniDatabase();

        Account account = new Account(
                1L,
                "ACC001",
                1000L
        );

        database.insert(account);

        System.out.println("After INSERT");
        System.out.println("Expected balance = 1000");
        System.out.println("Actual balance   = " + database.select(1L).balance());

        Account current = database.select(1L);

        Account updated = new Account(
                current.id(),
                current.accountNumber(),
                current.balance() - 100
        );

        database.update(updated);

        System.out.println();
        System.out.println("After UPDATE");
        System.out.println("Expected balance = 900");
        System.out.println("Actual balance   = " + database.select(1L).balance());

        database.delete(1L);

        System.out.println();
        System.out.println("After DELETE");
        System.out.println("Expected account = null");
        System.out.println("Actual account   = " + database.select(1L));
    }
}
