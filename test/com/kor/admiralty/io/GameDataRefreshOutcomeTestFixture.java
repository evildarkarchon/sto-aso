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
import java.util.List;
import java.util.Set;

/**
 * Exposes package-owned outcome factories to adapter tests without widening the
 * production API.
 */
public final class GameDataRefreshOutcomeTestFixture {

    private GameDataRefreshOutcomeTestFixture() {
    }

    /**
     * Creates an unchanged outcome for a caller test.
     *
     * @return current GameData Refresh outcome
     */
    public static GameDataRefreshOutcome current() {
        return GameDataRefreshOutcome.current();
    }

    /**
     * Creates a committed outcome for a caller test.
     *
     * @param changedFiles immutable changed-file content
     * @return refreshed GameData Refresh outcome
     */
    public static GameDataRefreshOutcome refreshed(Set<String> changedFiles) {
        return GameDataRefreshOutcome.refreshed(changedFiles);
    }

    /**
     * Creates an operational failure outcome for a caller test.
     *
     * @param category          stable failure phase
     * @param diagnosticCause   retained diagnostic cause
     * @param recoveryDirectory retained recovery location
     * @return failed GameData Refresh outcome
     */
    public static GameDataRefreshOutcome failed(
            GameDataRefreshOutcome.FailureCategory category,
            Throwable diagnosticCause,
            Path recoveryDirectory) {
        return GameDataRefreshOutcome.failed(category, diagnosticCause, recoveryDirectory);
    }

    /**
     * Adds non-fatal cleanup diagnostics to an outcome for a caller test.
     *
     * @param outcome     primary refresh outcome
     * @param diagnostics cleanup warnings to attach
     * @return equivalent outcome carrying cleanup warnings
     */
    public static GameDataRefreshOutcome withCleanupDiagnostics(
            GameDataRefreshOutcome outcome,
            List<? extends Throwable> diagnostics) {
        return outcome.withCleanupDiagnostics(diagnostics);
    }
}
