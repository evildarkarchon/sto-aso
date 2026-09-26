/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty.ui;

import com.kor.admiralty.App;
import com.kor.admiralty.AppBootstrap;
import com.kor.admiralty.io.GameDataRefresh;

import java.nio.file.Path;

/**
 * Probes the real code-source and application-bootstrap behavior from a child
 * JVM launched against either exploded classes or the packaged Admiralty JAR.
 */
public final class BuildArtifactBootstrapProbe {

    private BuildArtifactBootstrapProbe() {
    }

    /**
     * Resolves the executable directory from the active classpath, bootstraps
     * the application, compares both paths with caller-supplied expectations, and
     * closes the owned Ship Artwork before the child process exits.
     *
     * @param args expected executable directory followed by expected GameData
     *             directory
     * @throws Exception if paths cannot be resolved or application bootstrap fails
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Expected executable-directory and GameData-directory arguments.");
        }

        Path workingDirectory = Path.of(System.getProperty("user.dir")).toRealPath();
        Path expectedExecutableDirectory = Path.of(args[0]).toRealPath();
        Path expectedDataDirectory = Path.of(args[1]).toRealPath();
        Path executableDirectory = AdmiraltyConsole
                .candidateExecutableDirectory(workingDirectory)
                .toRealPath();

        if (!expectedExecutableDirectory.equals(executableDirectory)) {
            throw new AssertionError(
                    "Expected executable directory " + expectedExecutableDirectory
                            + " but resolved " + executableDirectory + ".");
        }

        AdmiraltyConsole.bootstrapApplication(
                executableDirectory,
                workingDirectory,
                new NoOpBackgroundJobs());

        try {
            Path dataDirectory = App.dataDir().toRealPath();
            if (!expectedDataDirectory.equals(dataDirectory)) {
                throw new AssertionError(
                        "Expected GameData directory " + expectedDataDirectory
                                + " but resolved " + dataDirectory + ".");
            }
        } finally {
            // The probe owns a real artwork lifetime even without constructing a frame.
            App.shipArtwork().close();
        }
    }

    /**
     * Prevents optional refresh work from escaping the synchronous bootstrap probe.
     */
    private static final class NoOpBackgroundJobs implements AppBootstrap.BackgroundJobs {

        /**
         * Accepts the optional GameData Refresh without starting background work.
         *
         * @param refresh scheduled application-owned refresh
         */
        @Override
        public void scheduleGameDataRefresh(GameDataRefresh refresh) {
            // The probe verifies startup and data-directory selection, not refresh execution.
        }

    }
}
