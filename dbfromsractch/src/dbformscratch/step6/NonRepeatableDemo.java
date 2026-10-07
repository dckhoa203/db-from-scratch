package dbformscratch.step6;

import dbformscratch.step6.database.MiniDatabase;
import dbformscratch.step6.model.Account;
import dbformscratch.step6.transaction.TransactionContext;
import dbformscratch.step6.transaction.TransactionManager;

public class NonRepeatableDemo {

    public static void main(String[] args) {

        MiniDatabase database = new MiniDatabase();

        database.insert(new Account(
                1L,
                "A",
                1000L
        ));

        TransactionManager txManager = new TransactionManager(database);

        TransactionContext t1 = txManager.begin();

        Account first = database.select(
                t1,
                1L
        );

        System.out.println(first);

        TransactionContext t2 = txManager.begin();

        database.update(t2, new Account(
                1L,
                "A",
                500L
        ));

        System.out.println("Update");
    }
}
