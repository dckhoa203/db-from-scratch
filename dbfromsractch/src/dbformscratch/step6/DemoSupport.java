package dbformscratch.step6;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class DemoSupport {

    private DemoSupport() {
    }

    static void await(CountDownLatch latch, String description) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out: " + description);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted: " + description, exception);
        }
    }

    static void join(Thread thread) {
        try {
            thread.join(2_000);
            if (thread.isAlive()) {
                throw new IllegalStateException("Demo thread did not finish: " + thread.getName());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while joining " + thread.getName(), exception);
        }
    }
}
