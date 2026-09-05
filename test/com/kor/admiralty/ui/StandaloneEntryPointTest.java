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
import com.kor.admiralty.AppTestFixture;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.io.GameDataRefresh;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Specifies the common bootstrap seam used before standalone UI and diagnostic
 * entry points access App state.
 */
class StandaloneEntryPointTest {

    private static final List<String> GAME_DATA_FILES = List.of(
            "ships.csv", "renamed.csv", "traits.csv", "events.csv", "assignments.csv");

    @TempDir
    Path tempDir;

    /**
     * Restores process-start state after exercising the shared static launch seam.
     */
    @AfterEach
    void resetApp() {
        AppTestFixture.reset();
    }

    /**
     * Verifies the shared entry-point helper publishes complete App state before
     * its caller constructs a frame or model.
     *
     * @throws Exception if fixture setup or bootstrap unexpectedly fails
     */
    @Test
    void sharedEntryPointBootstrapPublishesAppStateBeforeReturning() throws Exception {
        Path dataDirectory = Files.createDirectory(tempDir.resolve("data"));
        copyGameData(dataDirectory);

        AdmiraltyConsole.bootstrapApplication(
                tempDir.resolve("executable"),
                dataDirectory,
                new NoOpBackgroundJobs());

        assertEquals(dataDirectory, App.dataDir());
        assertEquals("U.S.S. Enterprise", App.gameData().ship("U.S.S. Enterprise").getName());
    }

    /**
     * Copies the complete small GameData fixture into a temporary directory.
     *
     * @param destination directory receiving the fixture files
     * @throws IOException if a fixture is absent or cannot be copied
     */
    private void copyGameData(Path destination) throws IOException {
        for (String filename : GAME_DATA_FILES) {
            try (InputStream fixture = getClass().getResourceAsStream("/gamedata/" + filename)) {
                if (fixture == null) {
                    throw new IOException("Missing test fixture: " + filename);
                }
                Files.copy(fixture, destination.resolve(filename));
            }
        }
    }

    /**
     * Keeps the test synchronous by accepting but not starting optional refresh
     * work.
     */
    private static final class NoOpBackgroundJobs implements AppBootstrap.BackgroundJobs {

        /**
         * Accepts optional GameData Refresh work without starting a background
         * thread.
         *
         * @param refresh scheduled application-owned refresh
         */
        @Override
        public void scheduleGameDataRefresh(GameDataRefresh refresh) {
            // The test exercises state publication, not optional background refresh
            // execution.
        }

        /**
         * Accepts optional Icon Cache work without starting a background thread.
         *
         * @param ship scheduled current-Roster Ship
         */
        @Override
        public void scheduleIconDownload(Ship ship) {
            // The test exercises state publication, not optional background refresh
            // execution.
        }
    }
}
