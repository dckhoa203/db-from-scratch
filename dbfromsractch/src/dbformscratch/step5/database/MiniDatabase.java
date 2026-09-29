package dbformscratch.step5.database;

import dbformscratch.step5.model.Account;
import dbformscratch.step5.transaction.TransactionContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MiniDatabase {

    private final Map<Long, Account> accounts = new ConcurrentHashMap<>();

    public void insert(Account account) {
        accounts.put(account.id(), account);
    }

    public void insert(TransactionContext transaction, Account account) {
        transaction.ensureActive();

        if (select(account.id()) != null) {
            throw new IllegalArgumentException("Account already exists: " + account.id());
        }

        if (transaction.recordWrite(account.id())) {
            transaction.record(
                    "TX-" + transaction.getTransactionId()
                            + " WRITE_SET += account=" + account.id()
            );
        }

        transaction.recordInsert(account.id());
        accounts.put(account.id(), account);

        transaction.record(
                "TX-" + transaction.getTransactionId()
                        + " INSERT account=" + account.id()
                        + " balance=" + account.balance()
        );
    }

    public Account select(long id) {
        return accounts.get(id);
    }

    public List<Account> selectByMinimumBalance(long minimumBalance) {
        return accounts.values().stream()
                .filter(account -> account.balance() >= minimumBalance)
                .toList();
    }

    public void update(TransactionContext transaction, Account account) {
        transaction.ensureActive();

        Account current = select(account.id());

        if (current == null) {
            throw new IllegalArgumentException("Account not found: " + account.id());
        }

        if (!transaction.insertedRow(account.id())
                && transaction.recordBeforeImage(account.id(), current)) {
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

        accounts.put(account.id(), account);

        transaction.record(
                "TX-" + transaction.getTransactionId()
                        + " UPDATE account=" + account.id()
                        + ": " + current.balance()
                        + " -> " + account.balance()
        );
    }

    public void restore(TransactionContext transaction) {
        transaction.getInsertedRows().forEach(rowId -> {
            Account inserted = accounts.remove(rowId);

            transaction.record(
                    "TX-" + transaction.getTransactionId()
                            + " REMOVE INSERTED account=" + rowId
                            + " balance=" + (inserted == null ? "<missing>" : inserted.balance())
            );
        });

        transaction.getBeforeImages().forEach((rowId, beforeImage) -> {
            Account current = accounts.put(rowId, beforeImage);

            transaction.record(
                    "TX-" + transaction.getTransactionId()
                            + " RESTORE account=" + rowId
                            + ": " + (current == null ? "<missing>" : current.balance())
                            + " -> " + beforeImage.balance()
            );
        });
    }
}
