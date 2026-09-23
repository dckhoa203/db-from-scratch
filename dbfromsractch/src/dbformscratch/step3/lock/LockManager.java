package dbformscratch.step3.lock;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class LockManager {

    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public void lock(long rowId) {

        ReentrantLock lock = locks.computeIfAbsent(
                rowId,
                id -> new ReentrantLock()
        );

        lock.lock();
    }

    public void unlock(long rowId) {

        ReentrantLock lock = locks.get(rowId);

        if (lock == null) {
            throw new IllegalStateException("No lock found for row " + rowId);
        }

        lock.unlock();
    }
}
