/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty.ui.workers;

import com.kor.admiralty.io.GameDataRefresh;
import com.kor.admiralty.io.GameDataRefreshOutcome;
import com.kor.admiralty.io.GameDataRefreshOutcome.FailureCategory;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static com.kor.admiralty.io.GameDataRefreshOutcomeTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Specifies the Swing adapter over the synchronous GameData Refresh seam.
 */
class UpdateDataFilesTest {

    private static final Logger LOGGER = Logger.getLogger(UpdateDataFiles.class.getName());

    /**
     * Executes one adapter and captures the requested number of completion log
     * records without changing the logger's production configuration.
     *
     * @param worker          adapter to execute
     * @param expectedRecords number of records expected from completion reporting
     * @return handler containing the captured records
     * @throws Exception if execution or reporting does not complete
     */
    private static RecordingHandler runAndRecord(UpdateDataFiles worker, int expectedRecords) throws Exception {
        RecordingHandler handler = new RecordingHandler(expectedRecords);
        LOGGER.addHandler(handler);
        try {
            worker.execute();
            worker.get(5, TimeUnit.SECONDS);
            assertTrue(handler.await());
            return handler;
        } finally {
            LOGGER.removeHandler(handler);
        }
    }

    /**
     * Verifies the secondary cleanup report retains its diagnostic and remains on
     * Swing's event-dispatch thread.
     *
     * @param handler           completed report capture
     * @param cleanupDiagnostic expected cleanup diagnostic
     */
    private static void assertCleanupWarning(RecordingHandler handler, Throwable cleanupDiagnostic) {
        assertEquals(Level.WARNING, handler.records().get(1).getLevel());
        assertTrue(handler.records().get(1).getMessage().contains("cleanup"));
        assertSame(cleanupDiagnostic, handler.records().get(1).getThrown());
        assertTrue(handler.allPublishedOnEventDispatchThread());
    }

    /**
     * Verifies the adapter performs exactly one synchronous refresh away from the
     * event-dispatch thread and preserves the immutable result.
     *
     * @throws Exception if background execution does not complete
     */
    @Test
    void backgroundWorkInvokesRefreshOnceAndReturnsItsOutcome() throws Exception {
        GameDataRefreshOutcome outcome = current();
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean invokedOnEventDispatchThread = new AtomicBoolean(true);
        UpdateDataFiles worker = new UpdateDataFiles(() -> {
            calls.incrementAndGet();
            invokedOnEventDispatchThread.set(SwingUtilities.isEventDispatchThread());
            return outcome;
        });

        RecordingHandler handler = new RecordingHandler(1);
        LOGGER.addHandler(handler);
        GameDataRefreshOutcome returnedOutcome;
        try {
            worker.execute();
            returnedOutcome = worker.get(5, TimeUnit.SECONDS);
            assertTrue(handler.await());
        } finally {
            LOGGER.removeHandler(handler);
        }

        assertSame(outcome, returnedOutcome);
        assertEquals(1, calls.get());
        assertFalse(invokedOnEventDispatchThread.get());
    }

    /**
     * Verifies current GameData is reported at information level on Swing's
     * event-dispatch thread.
     *
     * @throws Exception if background execution or completion reporting stalls
     */
    @Test
    void currentOutcomeIsReportedOnEventDispatchThread() throws Exception {
        RecordingHandler handler = runAndRecord(new UpdateDataFiles(() -> current()), 1);

        assertEquals(Level.INFO, handler.records().getFirst().getLevel());
        assertTrue(handler.records().getFirst().getMessage().contains("current"));
        assertTrue(handler.allPublishedOnEventDispatchThread());
    }

    /**
     * Verifies a committed refresh names the immutable changed-file set and tells
     * the player when those files become active.
     *
     * @throws Exception if background execution or completion reporting stalls
     */
    @Test
    void refreshedOutcomeReportsChangedFilesAndRestartGuidance() throws Exception {
        GameDataRefreshOutcome outcome = refreshed(Set.of("ships.csv", "events.csv"));

        RecordingHandler handler = runAndRecord(new UpdateDataFiles(() -> outcome), 1);

        String message = handler.records().getFirst().getMessage();
        assertEquals(Level.INFO, handler.records().getFirst().getLevel());
        assertTrue(message.contains("ships.csv"));
        assertTrue(message.contains("events.csv"));
        assertTrue(message.contains("restart ASO"));
        assertTrue(handler.allPublishedOnEventDispatchThread());
    }

    /**
     * Verifies operational failure reporting preserves the stable category,
     * diagnostic cause, and retained recovery location.
     *
     * @throws Exception if background execution or completion reporting stalls
     */
    @Test
    void failedOutcomeReportsCategoryDiagnosticAndRecoveryLocation() throws Exception {
        IllegalStateException diagnostic = new IllegalStateException("replacement rejected");
        Path recoveryDirectory = Path.of("retained-refresh-recovery");
        GameDataRefreshOutcome outcome = failed(
                FailureCategory.RECOVERY,
                diagnostic,
                recoveryDirectory);

        RecordingHandler handler = runAndRecord(new UpdateDataFiles(() -> outcome), 1);

        LogRecord record = handler.records().getFirst();
        assertEquals(Level.WARNING, record.getLevel());
        assertTrue(record.getMessage().contains(FailureCategory.RECOVERY.name()));
        assertTrue(record.getMessage().contains(recoveryDirectory.toString()));
        assertSame(diagnostic, record.getThrown());
        assertTrue(handler.allPublishedOnEventDispatchThread());
    }

    /**
     * Verifies cleanup trouble is logged separately without changing a successful
     * current outcome into a refresh failure.
     *
     * @throws Exception if background execution or completion reporting stalls
     */
    @Test
    void cleanupWarningDoesNotMisrepresentCurrentGameDataAsFailed() throws Exception {
        Exception cleanupDiagnostic = new Exception("staging directory retained");
        GameDataRefreshOutcome outcome = withCleanupDiagnostics(current(), List.of(cleanupDiagnostic));

        RecordingHandler handler = runAndRecord(new UpdateDataFiles(() -> outcome), 2);

        assertEquals(Level.INFO, handler.records().get(0).getLevel());
        assertTrue(handler.records().get(0).getMessage().contains("current"));
        assertFalse(handler.records().get(0).getMessage().contains("failed"));
        assertCleanupWarning(handler, cleanupDiagnostic);
    }

    /**
     * Verifies cleanup trouble remains secondary to a committed refresh and does
     * not suppress its changed-file or restart reporting.
     *
     * @throws Exception if background execution or completion reporting stalls
     */
    @Test
    void cleanupWarningDoesNotMisrepresentRefreshedGameDataAsFailed() throws Exception {
        Exception cleanupDiagnostic = new Exception("staging directory retained");
        GameDataRefreshOutcome outcome = withCleanupDiagnostics(
                refreshed(Set.of("ships.csv")),
                List.of(cleanupDiagnostic));

        RecordingHandler handler = runAndRecord(new UpdateDataFiles(() -> outcome), 2);

        assertEquals(Level.INFO, handler.records().get(0).getLevel());
        assertTrue(handler.records().get(0).getMessage().contains("ships.csv"));
        assertTrue(handler.records().get(0).getMessage().contains("restart ASO"));
        assertFalse(handler.records().get(0).getMessage().contains("failed"));
        assertCleanupWarning(handler, cleanupDiagnostic);
    }

    /**
     * Verifies programming failures remain exceptional worker completions and are
     * reported as execution failures rather than operational outcomes.
     *
     * @throws Exception if completion reporting stalls
     */
    @Test
    void programmingFailureSurfacesAsExecutionFailure() throws Exception {
        IllegalArgumentException programmingFailure = new IllegalArgumentException("broken invariant");
        UpdateDataFiles worker = new UpdateDataFiles(() -> {
            throw programmingFailure;
        });
        RecordingHandler handler = new RecordingHandler(1);
        LOGGER.addHandler(handler);
        try {
            worker.execute();

            ExecutionException executionFailure = assertThrows(
                    ExecutionException.class,
                    () -> worker.get(5, TimeUnit.SECONDS));
            assertSame(programmingFailure, executionFailure.getCause());
            assertTrue(handler.await());
        } finally {
            LOGGER.removeHandler(handler);
        }

        LogRecord record = handler.records().getFirst();
        assertTrue(record.getMessage().contains("execution failure"));
        assertSame(programmingFailure, record.getThrown());
        assertTrue(handler.allPublishedOnEventDispatchThread());
    }

    /**
     * Verifies the compatibility surface is gone: production constructs the final
     * adapter only from the application-owned GameData Refresh.
     */
    @Test
    void adapterSurfaceIsFinalAndAcceptsOnlyGameDataRefresh() {
        Set<String> protectedMethods = Arrays.stream(UpdateDataFiles.class.getDeclaredMethods())
                .filter(method -> Modifier.isProtected(method.getModifiers()))
                .map(java.lang.reflect.Method::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(Modifier.isFinal(UpdateDataFiles.class.getModifiers()));
        assertEquals(1, UpdateDataFiles.class.getConstructors().length);
        assertEquals(
                List.of(GameDataRefresh.class),
                List.of(UpdateDataFiles.class.getConstructors()[0].getParameterTypes()));
        assertEquals(Set.of("doInBackground", "done"), protectedMethods);
        assertEquals(0, UpdateDataFiles.class.getDeclaredClasses().length);
    }

    /**
     * Captures completion logs together with the Swing thread that published
     * them.
     */
    private static final class RecordingHandler extends Handler {

        private final CountDownLatch expectedRecords;
        private final List<LogRecord> records = new ArrayList<LogRecord>();
        private boolean allPublishedOnEventDispatchThread = true;

        /**
         * Creates a handler waiting for the complete expected report.
         *
         * @param expectedRecordCount number of log records that complete the report
         */
        private RecordingHandler(int expectedRecordCount) {
            expectedRecords = new CountDownLatch(expectedRecordCount);
        }

        /**
         * Records one log event and whether its publisher is Swing's event thread.
         *
         * @param record event published by the adapter
         */
        @Override
        public synchronized void publish(LogRecord record) {
            records.add(record);
            allPublishedOnEventDispatchThread &= SwingUtilities.isEventDispatchThread();
            expectedRecords.countDown();
        }

        /**
         * Performs no work because records remain in memory.
         */
        @Override
        public void flush() {
            // Records are retained in memory and require no flushing.
        }

        /**
         * Performs no work because registration, rather than this handler, owns
         * its lifecycle.
         */
        @Override
        public void close() {
            // The logger owns handler registration; this fixture owns no resources.
        }

        /**
         * Waits briefly for Swing completion reporting.
         *
         * @return whether every expected record arrived
         * @throws InterruptedException if the test thread is interrupted
         */
        private boolean await() throws InterruptedException {
            return expectedRecords.await(5, TimeUnit.SECONDS);
        }

        /**
         * Returns a stable snapshot of captured records.
         *
         * @return captured records in publication order
         */
        private synchronized List<LogRecord> records() {
            return List.copyOf(records);
        }

        /**
         * Reports whether every captured record was published on Swing's event
         * thread.
         *
         * @return {@code true} when all records came from the event-dispatch thread
         */
        private synchronized boolean allPublishedOnEventDispatchThread() {
            return allPublishedOnEventDispatchThread;
        }
    }
}
