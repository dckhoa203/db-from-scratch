import dbformscratch.database.MiniDatabase;
import dbformscratch.model.Account;

public class Main {
    public static void main(String[] args) {

        MiniDatabase database = new MiniDatabase();

        Account account = new Account(
                1L,
                "ACC001",
                1000L
        );

        database.insert(account);

        System.out.println(database.select(1L));

        Account current = database.select(1L);

        Account updated = new Account(
                current.id(),
                current.accountNumber(),
                current.balance() - 100
        );

        database.update(updated);

        System.out.println(database.select(1L));

        database.delete(1L);

        System.out.println(database.select(1L));
    }
}