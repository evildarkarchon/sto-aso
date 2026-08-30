/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.kor.admiralty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.Ship;

/**
 * Specifies ordered application startup through the AppBootstrap seam.
 */
class AppBootstrapTest {

    private static final List<String> GAME_DATA_FILES = List.of(
            "ships.csv", "renamed.csv", "traits.csv", "events.csv", "assignments.csv");

    @TempDir
    Path tempDir;

    /**
     * Starts every bootstrap scenario with an unpublished App holder.
     */
    @BeforeEach
    void resetAppBeforeTest() {
        App.resetForTesting();
    }

    /**
     * Prevents static application state from leaking into unrelated tests.
     */
    @AfterEach
    void resetAppAfterTest() {
        App.resetForTesting();
    }

    /**
     * Verifies the executable directory wins when it contains the required Ships marker file.
     *
     * @throws Exception if fixture setup or bootstrap unexpectedly fails
     */
    @Test
    void executableDirectoryContainingShipsFileWins() throws Exception {
        Path executableDirectory = Files.createDirectory(tempDir.resolve("executable"));
        Path workingDirectory = Files.createDirectory(tempDir.resolve("working"));
        copyGameData(executableDirectory);
        copyGameData(workingDirectory);

        new AppBootstrap(executableDirectory, workingDirectory, new RecordingBackgroundJobs()).bootstrap();

        assertEquals(executableDirectory, App.dataDir());
    }

    /**
     * Verifies startup falls back to the working directory when no Ships marker is beside the executable.
     *
     * @throws Exception if fixture setup or bootstrap unexpectedly fails
     */
    @Test
    void executableDirectoryWithoutShipsFileFallsBackToWorkingDirectory() throws Exception {
        Path executableDirectory = Files.createDirectory(tempDir.resolve("executable"));
        Path workingDirectory = Files.createDirectory(tempDir.resolve("working"));
        copyGameData(workingDirectory);

        new AppBootstrap(executableDirectory, workingDirectory, new RecordingBackgroundJobs()).bootstrap();

        assertEquals(workingDirectory, App.dataDir());
    }

    /**
     * Verifies loaded Admirals are attached before validation drops an unknown saved Ship.
     *
     * @throws Exception if fixture setup or bootstrap unexpectedly fails
     */
    @Test
    void admiralsAreAttachedBeforeValidation() throws Exception {
        Path dataDirectory = Files.createDirectory(tempDir.resolve("data"));
        copyGameData(dataDirectory);
        copyResource("/admirals/existing-admirals.xml", dataDirectory.resolve("admirals.xml"));

        new AppBootstrap(tempDir.resolve("executable"), dataDirectory, new RecordingBackgroundJobs()).bootstrap();

        Admiral admiral = App.admirals().findByName("Existing Admiral");
        assertNotNull(admiral);
        assertTrue(admiral.getOneTime().isEmpty());
        assertEquals(List.of("Class F Shuttle"), admiral.getActive());
        assertEquals(1, admiral.getActiveShips().size());
    }

    /**
     * Verifies an old hashes file requests one download-only GameData refresh.
     *
     * @throws Exception if fixture setup or bootstrap unexpectedly fails
     */
    @Test
    void staleHashesFileSchedulesOneDataFileUpdate() throws Exception {
        Path dataDirectory = Files.createDirectory(tempDir.resolve("data"));
        copyGameData(dataDirectory);
        Path hashesFile = Files.writeString(dataDirectory.resolve("hashes.md5"), "ships.csv=stale");
        Files.setLastModifiedTime(
                hashesFile,
                FileTime.from(Instant.now().minus(8, ChronoUnit.DAYS)));
        RecordingBackgroundJobs jobs = new RecordingBackgroundJobs();

        new AppBootstrap(tempDir.resolve("executable"), dataDirectory, jobs).bootstrap();

        assertEquals(List.of(dataDirectory), jobs.dataFileUpdates);
    }

    /**
     * Verifies a missing hashes file also requests exactly one download-only GameData refresh.
     *
     * @throws Exception if fixture setup or bootstrap unexpectedly fails
     */
    @Test
    void missingHashesFileSchedulesOneDataFileUpdate() throws Exception {
        Path dataDirectory = Files.createDirectory(tempDir.resolve("data"));
        copyGameData(dataDirectory);
        RecordingBackgroundJobs jobs = new RecordingBackgroundJobs();

        new AppBootstrap(tempDir.resolve("executable"), dataDirectory, jobs).bootstrap();

        assertEquals(List.of(dataDirectory), jobs.dataFileUpdates);
    }

    /**
     * Verifies a recent hashes file does not request a GameData refresh.
     *
     * @throws Exception if fixture setup or bootstrap unexpectedly fails
     */
    @Test
    void freshHashesFileSchedulesNoDataFileUpdate() throws Exception {
        Path dataDirectory = Files.createDirectory(tempDir.resolve("data"));
        copyGameData(dataDirectory);
        writeFreshHashes(dataDirectory);
        RecordingBackgroundJobs jobs = new RecordingBackgroundJobs();

        new AppBootstrap(tempDir.resolve("executable"), dataDirectory, jobs).bootstrap();

        assertTrue(jobs.dataFileUpdates.isEmpty());
    }

    /**
     * Verifies a stale Icon Cache requests downloads only for Ships owned by loaded Admirals.
     *
     * @throws Exception if fixture setup or bootstrap unexpectedly fails
     */
    @Test
    void staleIconCacheSchedulesOwnedShipsOnly() throws Exception {
        Path dataDirectory = Files.createDirectory(tempDir.resolve("data"));
        copyGameData(dataDirectory);
        copyResource("/admirals/existing-admirals.xml", dataDirectory.resolve("admirals.xml"));
        writeFreshHashes(dataDirectory);
        writeEmptyIconCache(dataDirectory, FileTime.from(Instant.EPOCH));
        RecordingBackgroundJobs jobs = new RecordingBackgroundJobs();

        new AppBootstrap(tempDir.resolve("executable"), dataDirectory, jobs).bootstrap();

        Set<String> scheduledShipNames = jobs.iconDownloads.stream()
                .map(Ship::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("Class F Shuttle", "Danube Runabout"), scheduledShipNames);
    }

    /**
     * Verifies a recent Icon Cache requests no Ship icon downloads.
     *
     * @throws Exception if fixture setup or bootstrap unexpectedly fails
     */
    @Test
    void freshIconCacheSchedulesNoIconDownloads() throws Exception {
        Path dataDirectory = Files.createDirectory(tempDir.resolve("data"));
        copyGameData(dataDirectory);
        copyResource("/admirals/existing-admirals.xml", dataDirectory.resolve("admirals.xml"));
        writeFreshHashes(dataDirectory);
        writeEmptyIconCache(dataDirectory, FileTime.from(Instant.now()));
        RecordingBackgroundJobs jobs = new RecordingBackgroundJobs();

        new AppBootstrap(tempDir.resolve("executable"), dataDirectory, jobs).bootstrap();

        assertTrue(jobs.iconDownloads.isEmpty());
    }

    /**
     * Verifies a corrupt derived Icon Cache is discarded and rebuilt instead of aborting application startup.
     *
     * @throws Exception if fixture setup unexpectedly fails
     */
    @Test
    void corruptIconCacheIsDiscardedAndSchedulesOwnedIcons() throws Exception {
        Path dataDirectory = Files.createDirectory(tempDir.resolve("data"));
        copyGameData(dataDirectory);
        copyResource("/admirals/existing-admirals.xml", dataDirectory.resolve("admirals.xml"));
        writeFreshHashes(dataDirectory);
        Path cacheFile = Files.writeString(dataDirectory.resolve("icons.zip"), "not a zip archive");
        RecordingBackgroundJobs jobs = new RecordingBackgroundJobs();
        AppBootstrap bootstrap = new AppBootstrap(tempDir.resolve("executable"), dataDirectory, jobs);

        assertDoesNotThrow(bootstrap::bootstrap);

        Set<String> scheduledShipNames = jobs.iconDownloads.stream()
                .map(Ship::getName)
                .collect(Collectors.toSet());
        assertFalse(Files.exists(cacheFile));
        assertEquals(Set.of("Class F Shuttle", "Danube Runabout"), scheduledShipNames);
        assertEquals(dataDirectory, App.dataDir());
    }

    /**
     * Verifies missing required Ships data aborts startup before state publication or job scheduling.
     *
     * @throws Exception if fixture setup unexpectedly fails
     */
    @Test
    void missingShipsFileRaisesBootstrapExceptionAndSchedulesNoJobs() throws Exception {
        Path dataDirectory = Files.createDirectory(tempDir.resolve("data"));
        copyGameData(dataDirectory);
        Files.delete(dataDirectory.resolve("ships.csv"));
        RecordingBackgroundJobs jobs = new RecordingBackgroundJobs();
        AppBootstrap bootstrap = new AppBootstrap(tempDir.resolve("executable"), dataDirectory, jobs);

        assertThrows(AppBootstrapException.class, bootstrap::bootstrap);

        assertTrue(jobs.dataFileUpdates.isEmpty());
        assertTrue(jobs.iconDownloads.isEmpty());
        assertThrows(IllegalStateException.class, App::dataDir);
    }

    /**
     * Copies the complete small GameData fixture into a temporary directory.
     *
     * @param destination directory receiving the fixture files
     * @throws IOException if a fixture is absent or cannot be copied
     */
    private void copyGameData(Path destination) throws IOException {
        for (String filename : GAME_DATA_FILES) {
            copyResource("/gamedata/" + filename, destination.resolve(filename));
        }
    }

    /**
     * Copies one classpath fixture to a caller-selected filesystem path.
     *
     * @param resourceName absolute classpath resource name
     * @param destination filesystem path receiving the fixture
     * @throws IOException if the fixture is absent or cannot be copied
     */
    private void copyResource(String resourceName, Path destination) throws IOException {
        try (InputStream fixture = getClass().getResourceAsStream(resourceName)) {
            if (fixture == null) {
                throw new IOException("Missing test fixture: " + resourceName);
            }
            Files.copy(fixture, destination);
        }
    }

    /**
     * Writes a recent hash manifest so icon scheduling tests isolate the Icon Cache decision.
     *
     * @param dataDirectory directory receiving {@code hashes.md5}
     * @throws IOException if the manifest cannot be written
     */
    private void writeFreshHashes(Path dataDirectory) throws IOException {
        Files.writeString(dataDirectory.resolve("hashes.md5"), "ships.csv=current");
    }

    /**
     * Writes a valid empty Icon Cache zip with a caller-controlled modification time.
     *
     * @param dataDirectory directory receiving {@code icons.zip}
     * @param modifiedTime cache timestamp used by the freshness decision
     * @throws IOException if the cache cannot be written or timestamped
     */
    private void writeEmptyIconCache(Path dataDirectory, FileTime modifiedTime) throws IOException {
        Path cacheFile = dataDirectory.resolve("icons.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(cacheFile))) {
            // A closed empty ZipOutputStream is a valid cache archive with no icon entries.
        }
        Files.setLastModifiedTime(cacheFile, modifiedTime);
    }

    /**
     * Records requested work without starting threads or touching the network.
     */
    private static final class RecordingBackgroundJobs implements AppBootstrap.BackgroundJobs {

        private final List<Path> dataFileUpdates = new ArrayList<Path>();
        private final List<Ship> iconDownloads = new ArrayList<Ship>();

        @Override
        public void scheduleDataFileUpdate(Path dataDirectory) {
            dataFileUpdates.add(dataDirectory);
        }

        @Override
        public void scheduleIconDownload(Ship ship) {
            iconDownloads.add(ship);
        }
    }
}
