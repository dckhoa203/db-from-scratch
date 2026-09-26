package dbformscratch.step4.database;

import dbformscratch.step4.model.Account;
import dbformscratch.step4.transaction.TransactionContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MiniDatabase {

    private final Map<Long, Account> accounts = new ConcurrentHashMap<>();

    public void insert(Account account) {
        accounts.put(account.id(), account);
    }

    public Account select(long id) {
        return accounts.get(id);
    }

    public void update(TransactionContext transaction, Account account) {

        transaction.ensureActive();

        Account current = select(account.id());

        if (current == null) {
            throw new IllegalArgumentException("Account not found: " + account.id());
        }

        if (transaction.recordBeforeImage(account.id(), current)) {
            transaction.record(
                    "TX-" + transaction.getTransactionId()
                            + " BEFORE_IMAGE account=" + account.id()
                            + " balance=" + current.balance()
            );
        }

        if (transaction.recordWrite(account.id())) {
            transaction.record(
                    "TX-" + transaction.getTransactionId()
                            + " WRITE_SET += account=" + account.id()
            );
        }

        applyUpdate(account);

        transaction.record(
                "TX-" + transaction.getTransactionId()
                        + " UPDATE account=" + account.id()
                        + ": " + current.balance()
                        + " -> " + account.balance()
        );
    }

    public void restore(TransactionContext transaction) {
        transaction.getBeforeImages().forEach((rowId, beforeImage) -> {
            Account current = accounts.put(rowId, beforeImage);

            transaction.record(
                    "TX-" + transaction.getTransactionId()
                            + " RESTORE account=" + rowId
                            + ": " + current.balance()
                            + " -> " + beforeImage.balance()
            );
        });
    }

    public void delete(long id) {
        accounts.remove(id);
    }

    private void applyUpdate(Account account) {
        accounts.put(account.id(), account);
    }
}
