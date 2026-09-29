package dbformscratch.step5;

import dbformscratch.step5.database.MiniDatabase;
import dbformscratch.step5.model.Account;
import dbformscratch.step5.transaction.TransactionContext;
import dbformscratch.step5.transaction.TransactionManager;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class IsolationProblemDemo {

    public static void main(String[] args) {
        dirtyRead();
        nonRepeatableRead();
        phantomRead();
        lostUpdate();
        rollbackOverwritesCommittedWrite();
    }

    private static void dirtyRead() {
        Timeline timeline = new Timeline("DIRTY READ");
        MiniDatabase database = databaseWithAccountA(1000L);
        TransactionManager manager = new TransactionManager(database);

        TransactionContext t1 = manager.begin();
        timeline.sync("T1", t1);
        Account original = read(timeline, "T1", t1, database, 1L);

        database.update(t1, accountWithBalance(original, 500L));
        timeline.sync("T1", t1);

        TransactionContext t2 = manager.begin();
        timeline.sync("T2", t2);
        Account dirtyValue = read(timeline, "T2", t2, database, 1L);
        timeline.log("T2 | DIRTY READ: saw " + dirtyValue.balance() + " before T1 committed");

        manager.rollback(t1);
        timeline.sync("T1", t1);
        manager.commit(t2);
        timeline.sync("T2", t2);

        long finalBalance = database.select(1L).balance();
        timeline.log("FINAL | A = " + finalBalance);
        timeline.log("Dirty read reproduced = "
                + (dirtyValue.balance() == 500L && finalBalance == 1000L));
        timeline.finish();
    }

    private static void nonRepeatableRead() {
        Timeline timeline = new Timeline("NON-REPEATABLE READ");
        MiniDatabase database = databaseWithAccountA(1000L);
        TransactionManager manager = new TransactionManager(database);

        TransactionContext t1 = manager.begin();
        timeline.sync("T1", t1);
        Account firstRead = read(timeline, "T1", t1, database, 1L);

        TransactionContext t2 = manager.begin();
        timeline.sync("T2", t2);
        Account current = read(timeline, "T2", t2, database, 1L);
        database.update(t2, accountWithBalance(current, 500L));
        timeline.sync("T2", t2);
        manager.commit(t2);
        timeline.sync("T2", t2);

        Account secondRead = read(timeline, "T1", t1, database, 1L);
        manager.commit(t1);
        timeline.sync("T1", t1);

        timeline.log("First read  = " + firstRead.balance());
        timeline.log("Second read = " + secondRead.balance());
        timeline.log("Non-repeatable read reproduced = "
                + (firstRead.balance() != secondRead.balance()));
        timeline.finish();
    }

    private static void phantomRead() {
        Timeline timeline = new Timeline("PHANTOM READ");
        MiniDatabase database = new MiniDatabase();
        database.insert(new Account(1L, "A", 1500L));
        database.insert(new Account(2L, "B", 500L));
        TransactionManager manager = new TransactionManager(database);

        TransactionContext t1 = manager.begin();
        timeline.sync("T1", t1);
        List<Account> firstRead = query(timeline, "T1", t1, database, 1000L);

        TransactionContext t2 = manager.begin();
        timeline.sync("T2", t2);
        database.insert(t2, new Account(3L, "C", 2000L));
        timeline.sync("T2", t2);
        manager.commit(t2);
        timeline.sync("T2", t2);

        List<Account> secondRead = query(timeline, "T1", t1, database, 1000L);
        manager.commit(t1);
        timeline.sync("T1", t1);

        timeline.log("First result  = " + accountNames(firstRead));
        timeline.log("Second result = " + accountNames(secondRead));
        timeline.log("Phantom read reproduced = " + (secondRead.size() > firstRead.size()));
        timeline.finish();
    }

    private static void lostUpdate() {
        Timeline timeline = new Timeline("LOST UPDATE");
        MiniDatabase database = databaseWithAccountA(1000L);
        TransactionManager manager = new TransactionManager(database);

        TransactionContext t1 = manager.begin();
        TransactionContext t2 = manager.begin();
        timeline.sync("T1", t1);
        timeline.sync("T2", t2);

        Account t1Read = read(timeline, "T1", t1, database, 1L);
        Account t2Read = read(timeline, "T2", t2, database, 1L);

        database.update(t1, accountWithBalance(t1Read, 900L));
        timeline.sync("T1", t1);
        manager.commit(t1);
        timeline.sync("T1", t1);

        database.update(t2, accountWithBalance(t2Read, 950L));
        timeline.sync("T2", t2);
        manager.commit(t2);
        timeline.sync("T2", t2);

        long finalBalance = database.select(1L).balance();
        timeline.log("T1 intended A = 900; T2 intended A = 950");
        timeline.log("FINAL | A = " + finalBalance);
        timeline.log("Lost update reproduced = " + (finalBalance == 950L));
        timeline.finish();
    }

    private static void rollbackOverwritesCommittedWrite() {
        Timeline timeline = new Timeline("ROLLBACK OVERWRITES COMMITTED WRITE");
        MiniDatabase database = databaseWithAccountA(1000L);
        TransactionManager manager = new TransactionManager(database);

        TransactionContext t1 = manager.begin();
        timeline.sync("T1", t1);
        Account t1Read = read(timeline, "T1", t1, database, 1L);
        database.update(t1, accountWithBalance(t1Read, 900L));
        timeline.sync("T1", t1);

        TransactionContext t2 = manager.begin();
        timeline.sync("T2", t2);
        Account t2Read = read(timeline, "T2", t2, database, 1L);
        database.update(t2, accountWithBalance(t2Read, 800L));
        timeline.sync("T2", t2);
        manager.commit(t2);
        timeline.sync("T2", t2);

        manager.rollback(t1);
        timeline.sync("T1", t1);

        long finalBalance = database.select(1L).balance();
        timeline.log("T2 committed A = 800");
        timeline.log("FINAL | A = " + finalBalance);
        timeline.log("Committed write overwritten by rollback = " + (finalBalance == 1000L));
        timeline.finish();
    }

    private static MiniDatabase databaseWithAccountA(long balance) {
        MiniDatabase database = new MiniDatabase();
        database.insert(new Account(1L, "A", balance));
        return database;
    }

    private static Account read(
            Timeline timeline,
            String actor,
            TransactionContext transaction,
            MiniDatabase database,
            long accountId
    ) {
        Account account = database.select(accountId);
        timeline.log(actor + " | TX-" + transaction.getTransactionId()
                + " SELECT account=" + accountId + " -> " + account.balance());
        return account;
    }

    private static List<Account> query(
            Timeline timeline,
            String actor,
            TransactionContext transaction,
            MiniDatabase database,
            long minimumBalance
    ) {
        List<Account> accounts = database.selectByMinimumBalance(minimumBalance);
        timeline.log(actor + " | TX-" + transaction.getTransactionId()
                + " SELECT balance >= " + minimumBalance
                + " -> " + accountNames(accounts));
        return accounts;
    }

    private static Account accountWithBalance(Account account, long balance) {
        return new Account(account.id(), account.accountNumber(), balance);
    }

    private static String accountNames(List<Account> accounts) {
        return accounts.stream()
                .map(Account::accountNumber)
                .sorted()
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static final class Timeline {

        private final Map<TransactionContext, Integer> seenEvents = new IdentityHashMap<>();
        private int sequence = 1;

        private Timeline(String title) {
            System.out.println();
            System.out.println("=== " + title + " ===");
        }

        private void sync(String actor, TransactionContext transaction) {
            List<String> events = transaction.getTrace();
            int seen = seenEvents.getOrDefault(transaction, 0);

            for (int index = seen; index < events.size(); index++) {
                log(actor + " | " + events.get(index));
            }

            seenEvents.put(transaction, events.size());
        }

        private void log(String message) {
            System.out.printf("%02d | %s%n", sequence++, message);
        }

        private void finish() {
            System.out.println();
        }
    }
}
