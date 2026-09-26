/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.artwork;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Internal monotonic clock, timer and monitor waits owned by one artwork lifetime. */
class ArtworkTiming {
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, task -> {
        Thread thread = new Thread(task, "ship-artwork-persistence");
        // Optional persistence must not keep the process alive after application shutdown.
        thread.setDaemon(true);
        return thread;
    });

    /** Cancelling a debounce must also release its retained callback promptly. */
    ArtworkTiming() {
        executor.setRemoveOnCancelPolicy(true);
    }

    long nanoTime() { return System.nanoTime(); }

    /** Schedules off the caller thread and returns a non-interrupting cancellation action. */
    Runnable schedule(Runnable action, long delayNanos) {
        var future = executor.schedule(action, delayNanos, TimeUnit.NANOSECONDS);
        return () -> future.cancel(false);
    }

    /** Releases the held monitor until notified or the remaining grace expires. */
    void await(Object monitor, long remainingNanos) throws InterruptedException {
        TimeUnit.NANOSECONDS.timedWait(monitor, remainingNanos);
    }

    /**
     * Waits for an operator-request completion while production remote requests enforce their own
     * timeouts. The caller must hold the monitor before entering this wait.
     * @throws InterruptedException if the operator interrupts the waiting thread
     */
    void awaitCompletion(Object monitor) throws InterruptedException {
        monitor.wait();
    }

    /** Cancels outstanding timers without waiting for executor termination. */
    void close() {
        executor.shutdownNow();
    }
}
