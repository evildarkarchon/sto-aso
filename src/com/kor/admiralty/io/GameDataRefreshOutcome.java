/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty.io;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable result of one synchronous GameData Refresh attempt. Its factories
 * keep status, changed-file, and failure evidence states unambiguous.
 */
public final class GameDataRefreshOutcome {

    /**
     * Caller-visible meaning of a completed refresh attempt.
     */
    public enum Status {
        CURRENT,
        REFRESHED,
        FAILED
    }

    /**
     * Stable operational phase in which a refresh could not complete.
     */
    public enum FailureCategory {
        REMOTE_ACQUISITION,
        VERIFICATION,
        INSTALLATION,
        RECOVERY
    }

    private final Status status;
    private final Set<String> changedFiles;
    private final FailureCategory failureCategory;
    private final Throwable diagnosticCause;
    private final Path recoveryDirectory;
    private final List<Throwable> cleanupDiagnostics;

    private GameDataRefreshOutcome(
            Status status,
            Set<String> changedFiles,
            FailureCategory failureCategory,
            Throwable diagnosticCause,
            Path recoveryDirectory,
            List<? extends Throwable> cleanupDiagnostics) {
        this.status = status;
        this.changedFiles = changedFiles;
        this.failureCategory = failureCategory;
        this.diagnosticCause = diagnosticCause;
        this.recoveryDirectory = recoveryDirectory;
        this.cleanupDiagnostics = List.copyOf(
                Objects.requireNonNull(cleanupDiagnostics, "cleanupDiagnostics"));
    }

    /**
     * Creates an outcome for a validated GameData set that needs no file changes.
     *
     * @return current outcome without change or failure claims
     */
    static GameDataRefreshOutcome current() {
        return new GameDataRefreshOutcome(Status.CURRENT, Set.of(), null, null, null, List.of());
    }

    /**
     * Creates an outcome for a committed refresh that changed at least one
     * GameData file.
     *
     * @param changedFiles exact filenames committed by the refresh
     * @return refreshed outcome with an immutable defensive copy of the filenames
     * @throws IllegalArgumentException if no files changed
     */
    static GameDataRefreshOutcome refreshed(Set<String> changedFiles) {
        Set<String> immutableChanges = Set.copyOf(Objects.requireNonNull(changedFiles, "changedFiles"));
        if (immutableChanges.isEmpty()) {
            throw new IllegalArgumentException("A refreshed outcome requires at least one changed file.");
        }
        return new GameDataRefreshOutcome(Status.REFRESHED, immutableChanges, null, null, null, List.of());
    }

    /**
     * Creates an operational failure without claiming that any GameData changes
     * were committed.
     *
     * @param category          stable phase that could not complete
     * @param diagnosticCause   underlying cause when one is available, otherwise {@code null}
     * @param recoveryDirectory retained recovery artifacts when rollback was incomplete, otherwise {@code null}
     * @return failed outcome retaining the supplied diagnostic evidence
     */
    static GameDataRefreshOutcome failed(
            FailureCategory category,
            Throwable diagnosticCause,
            Path recoveryDirectory) {
        return new GameDataRefreshOutcome(
                Status.FAILED,
                Set.of(),
                Objects.requireNonNull(category, "category"),
                diagnosticCause,
                recoveryDirectory,
                List.of());
    }

    /**
     * Returns an equivalent immutable outcome carrying additional non-fatal
     * private-artifact cleanup diagnostics.
     *
     * @param diagnostics cleanup problems to expose to the reporting adapter
     * @return this outcome when no diagnostics were supplied, otherwise a copy
     */
    GameDataRefreshOutcome withCleanupDiagnostics(List<? extends Throwable> diagnostics) {
        Objects.requireNonNull(diagnostics, "diagnostics");
        if (diagnostics.isEmpty()) {
            return this;
        }
        List<Throwable> combinedDiagnostics = new ArrayList<Throwable>(cleanupDiagnostics);
        combinedDiagnostics.addAll(diagnostics);
        return new GameDataRefreshOutcome(
                status,
                changedFiles,
                failureCategory,
                diagnosticCause,
                recoveryDirectory,
                combinedDiagnostics);
    }

    /**
     * Returns the caller-visible result status.
     *
     * @return current, refreshed, or failed
     */
    public Status status() {
        return status;
    }

    /**
     * Returns the immutable filenames committed by a successful changed refresh.
     *
     * @return empty set unless the status is {@link Status#REFRESHED}
     */
    public Set<String> changedFiles() {
        return changedFiles;
    }

    /**
     * Returns the stable operational failure phase when the attempt failed.
     *
     * @return failure category, or empty for successful outcomes
     */
    public Optional<FailureCategory> failureCategory() {
        return Optional.ofNullable(failureCategory);
    }

    /**
     * Returns the underlying operational cause when one was available.
     *
     * @return diagnostic cause, or empty when no cause was retained
     */
    public Optional<Throwable> diagnosticCause() {
        return Optional.ofNullable(diagnosticCause);
    }

    /**
     * Returns recovery artifacts retained after an incomplete rollback.
     *
     * @return retained directory, or empty when no manual recovery is needed
     */
    public Optional<Path> recoveryDirectory() {
        return Optional.ofNullable(recoveryDirectory);
    }

    /**
     * Returns non-fatal failures encountered while removing private transaction
     * artifacts after the reported result was already determined.
     *
     * @return immutable cleanup diagnostics, empty when cleanup completed
     */
    public List<Throwable> cleanupDiagnostics() {
        return cleanupDiagnostics;
    }
}
