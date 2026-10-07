package dbformscratch.step6.trace;

import java.util.ArrayList;
import java.util.List;

/** Demo-only event log that preserves a readable global order across threads. */
public class TransactionTrace {

    private final List<String> events = new ArrayList<>();

    public synchronized void event(long transactionId, String message) {
        String line = String.format("%02d T%d %s", events.size() + 1, transactionId, message);
        events.add(line);
        System.out.println(line);
        notifyAll();
    }

    public synchronized void system(String message) {
        String line = String.format("%02d SYS %s", events.size() + 1, message);
        events.add(line);
        System.out.println(line);
        notifyAll();
    }

    public synchronized void awaitEventContaining(String text) {
        long deadline = System.currentTimeMillis() + 2_000;
        while (events.stream().noneMatch(event -> event.contains(text))) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                throw new IllegalStateException("Timed out waiting for trace event: " + text);
            }
            try {
                wait(remaining);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for trace event", exception);
            }
        }
    }
}
