/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty.ui.workers;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingWorker;

import com.kor.admiralty.io.GameDataRefresh;
import com.kor.admiralty.io.GameDataRefreshOutcome;

/**
 * Runs one synchronous GameData Refresh away from Swing's event-dispatch thread
 * and reports its immutable outcome after completion.
 */
public final class UpdateDataFiles extends SwingWorker<GameDataRefreshOutcome, Void> {

    private static final Logger LOGGER = Logger.getLogger(UpdateDataFiles.class.getName());

    private final Supplier<GameDataRefreshOutcome> refreshOperation;

    /**
     * Creates the Swing adapter for an application-owned GameData Refresh.
     *
     * @param refresh exact instance already consulted and scheduled by bootstrap
     * @throws NullPointerException if {@code refresh} is null
     */
    public UpdateDataFiles(GameDataRefresh refresh) {
        this(asOperation(refresh));
    }

    /**
     * Creates an adapter with a deterministic synchronous operation for focused
     * Swing lifecycle tests.
     *
     * @param refreshOperation operation producing one immutable refresh outcome
     * @throws NullPointerException if {@code refreshOperation} is null
     */
    UpdateDataFiles(Supplier<GameDataRefreshOutcome> refreshOperation) {
        this.refreshOperation = Objects.requireNonNull(refreshOperation, "refreshOperation");
    }

    /**
     * Converts the supplied module instance into the operation executed by this
     * worker without reconstructing it from its data directory.
     *
     * @param refresh application-owned GameData Refresh
     * @return synchronous operation bound to the exact supplied instance
     */
    private static Supplier<GameDataRefreshOutcome> asOperation(GameDataRefresh refresh) {
        Objects.requireNonNull(refresh, "refresh");
        return refresh::refresh;
    }

    /**
     * Logs one immutable outcome and any separate non-fatal cleanup diagnostics.
     *
     * @param outcome completed GameData Refresh outcome
     */
    private static void report(GameDataRefreshOutcome outcome) {
        switch (outcome.status()) {
            case CURRENT -> LOGGER.info("GameData Refresh found the installed GameData current.");
            case REFRESHED -> LOGGER.info(
                    "GameData Refresh replaced " + outcome.changedFiles()
                            + "; restart ASO to apply the refreshed files.");
            case FAILED -> reportFailure(outcome);
        }

        for (Throwable cleanupDiagnostic : outcome.cleanupDiagnostics()) {
            LOGGER.log(
                    Level.WARNING,
                    "GameData Refresh cleanup warning; the primary outcome remains " + outcome.status() + ".",
                    cleanupDiagnostic);
        }
    }

    /**
     * Logs stable operational failure evidence without inventing a successful or
     * refreshed state.
     *
     * @param outcome failed outcome carrying its category and optional evidence
     */
    private static void reportFailure(GameDataRefreshOutcome outcome) {
        String message = "GameData Refresh failed during " + outcome.failureCategory().orElseThrow().name() + ".";
        if (outcome.recoveryDirectory().isPresent()) {
            message += " Recovery files remain in " + outcome.recoveryDirectory().orElseThrow() + ".";
        }

        if (outcome.diagnosticCause().isPresent()) {
            LOGGER.log(Level.WARNING, message, outcome.diagnosticCause().orElseThrow());
        } else {
            LOGGER.warning(message);
        }
    }

    /**
     * Invokes the synchronous refresh exactly once on this worker's background
     * thread. Programming failures propagate through SwingWorker's execution
     * result rather than being converted to operational outcomes.
     *
     * @return immutable outcome returned by the GameData Refresh
     */
    @Override
    protected GameDataRefreshOutcome doInBackground() {
        return refreshOperation.get();
    }

    /**
     * Reports the completed refresh on Swing's event-dispatch thread. SwingWorker
     * owns this callback's dispatch contract; this adapter performs no reporting
     * from its background operation.
     */
    @Override
    protected void done() {
        try {
            report(get());
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Interrupted while reporting GameData Refresh completion.", cause);
        } catch (ExecutionException cause) {
            LOGGER.log(Level.SEVERE, "GameData Refresh execution failure.", cause.getCause());
        }
    }
}
