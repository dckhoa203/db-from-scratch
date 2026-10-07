package dbformscratch.step6;

import dbformscratch.step6.database.MiniDatabase;
import dbformscratch.step6.model.Account;
import dbformscratch.step6.trace.TransactionTrace;
import dbformscratch.step6.transaction.TransactionContext;
import dbformscratch.step6.transaction.TransactionManager;

/** A sole reader can upgrade S(A) to X(A) before writing. */
public class LockUpgradeDemo {

    public static void main(String[] args) {
        System.out.println("=== A sole reader upgrades S(A) to X(A) ===");
        TransactionTrace trace = new TransactionTrace();
        MiniDatabase database = new MiniDatabase(trace);
        database.insert(new Account(1L, "A", 1000L));
        TransactionManager transactions = new TransactionManager(database);

        TransactionContext t1 = transactions.begin();
        database.select(t1, 1L);
        database.update(t1, new Account(1L, "A", 900L));
        transactions.commit(t1);
    }
}
