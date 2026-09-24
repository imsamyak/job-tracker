package org.example.service;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Shuts the app down a fixed delay after it's asked to, UNLESS some other request arrives first - see
 * {@code ShutdownCancelFilter}, which cancels the pending shutdown on any request path other than
 * {@code /api/shutdown} itself. Asking again while one is already pending simply reschedules it.
 */
@Slf4j
public class ShutdownService {

    private final ScheduledExecutorService scheduler;
    private final Runnable terminate;
    private ScheduledFuture<?> pending;

    /** @param terminate what "shut down" actually does; swapped for a no-op in tests. */
    public ShutdownService(ScheduledExecutorService scheduler, Runnable terminate) {
        this.scheduler = scheduler;
        this.terminate = terminate;
    }

    public synchronized void scheduleShutdown(Duration delay) {
        cancelPending();
        pending = scheduler.schedule(this::runTerminate, delay.toMillis(), TimeUnit.MILLISECONDS);
        log.info("Shutdown scheduled in {}", delay);
    }

    /** @return true if a pending shutdown was actually cancelled */
    public synchronized boolean cancelPending() {
        boolean wasPending = pending != null && !pending.isDone();
        if (wasPending) {
            pending.cancel(false);
        }
        pending = null;
        return wasPending;
    }

    public synchronized boolean isPending() {
        return pending != null && !pending.isDone();
    }

    private void runTerminate() {
        log.info("Shutting down - nothing else used the app during the grace period");
        terminate.run();
    }
}
