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

import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.io.AdmiralsStoreException;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.io.GameDataRefresh;
import com.kor.admiralty.ui.artwork.ShipArtwork;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

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
     * Verifies the executable directory wins when it contains the required Ships
     * marker file.
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
     * Verifies startup falls back to the working directory when no Ships marker is
     * beside the executable.
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
     * Verifies an IDE launch from the repository root finds GameData in its data
     * subdirectory when no Ships marker is beside the classes or in the root.
     *
     * @throws Exception if fixture setup or bootstrap unexpectedly fails
     */
    @Test
    void repositoryWorkingDirectoryUsesDataSubdirectory() throws Exception {
        Path executableDirectory = Files.createDirectory(tempDir.resolve("classes"));
        Path dataDirectory = Files.createDirectory(tempDir.resolve("data"));
        copyGameData(dataDirectory);

        new AppBootstrap(executableDirectory, tempDir, new RecordingBackgroundJobs()).bootstrap();

        assertEquals(dataDirectory, App.dataDir());
    }

    /**
     * Verifies startup receives canonically restored Admirals that are ready for
     * immediate Roster lookup.
     *
     * @throws Exception if fixture setup or bootstrap unexpectedly fails
     */
    @Test
    void admiralsLoadReadyForUseThroughConstructionSafePath() throws Exception {
        Path dataDirectory = Files.createDirectory(tempDir.resolve("data"));
        copyGameData(dataDirectory);
        copyResource("/admirals/existing-admirals.xml", dataDirectory.resolve("admirals.xml"));

        new AppBootstrap(tempDir.resolve("executable"), dataDirectory, new RecordingBackgroundJobs()).bootstrap();

        Admiral admiral = App.admirals().findByName("Existing Admiral");
        assertNotNull(admiral);
        assertTrue(admiral.getRoster().getOneTimeCards().isEmpty());
        assertEquals(
                List.of("Class F Shuttle"),
                admiral.getRoster().getActiveCards().stream()
                        .map(card -> card.getShip().getName())
                        .collect(Collectors.toList()));
    }

    /**
     * Verifies corrupt Admiral restoration publishes no App state and schedules no
     * background work.
     *
     * @throws Exception if fixture setup unexpectedly fails
     */
    @Test
    void corruptAdmiralRestorationPublishesNothing() throws Exception {
        Path dataDirectory = Files.createDirectory(tempDir.resolve("data"));
        copyGameData(dataDirectory);
        Files.writeString(
                dataDirectory.resolve("admirals.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<admirals><admiral prioritizeActive=\"true\">"
                        + "<name>Valid Name</name><faction>Federation</faction>"
                        + "<usage><entry><key>Class F Shuttle</key><value>-1</value></entry></usage>"
                        + "</admiral></admirals>");
        RecordingBackgroundJobs jobs = new RecordingBackgroundJobs();

        AppBootstrapException failure = assertThrows(
                AppBootstrapException.class,
                () -> new AppBootstrap(tempDir.resolve("executable"), dataDirectory, jobs).bootstrap());

        assertInstanceOf(AdmiralsStoreException.class, failure.getCause());
        assertTrue(jobs.gameDataRefreshes.isEmpty());
        assertThrows(IllegalStateException.class, App::gameData);
        assertThrows(IllegalStateException.class, App::admirals);
        assertThrows(IllegalStateException.class, App::dataDir);
    }

    /**
     * Verifies an old hashes file requests one download-only GameData Refresh.
     *
     * @throws Exception if fixture setup or bootstrap unexpectedly fails
     */
    @Test
    void staleHashesFileSchedulesOneGameDataRefresh() throws Exception {
        Path dataDirectory = Files.createDirectory(tempDir.resolve("data"));
        copyGameData(dataDirectory);
        Path hashesFile = Files.writeString(dataDirectory.resolve("hashes.md5"), "ships.csv=stale");
        Files.setLastModifiedTime(
                hashesFile,
                FileTime.from(Instant.now().minus(8, ChronoUnit.DAYS)));
        RecordingBackgroundJobs jobs = new RecordingBackgroundJobs();

        new AppBootstrap(tempDir.resolve("executable"), dataDirectory, jobs).bootstrap();

        assertEquals(1, jobs.gameDataRefreshes.size());
    }

    /**
     * Verifies a missing hashes file also requests exactly one download-only
     * GameData Refresh.
     *
     * @throws Exception if fixture setup or bootstrap unexpectedly fails
     */
    @Test
    void missingHashesFileSchedulesOneGameDataRefresh() throws Exception {
        Path dataDirectory = Files.createDirectory(tempDir.resolve("data"));
        copyGameData(dataDirectory);
        RecordingBackgroundJobs jobs = new RecordingBackgroundJobs();

        new AppBootstrap(tempDir.resolve("executable"), dataDirectory, jobs).bootstrap();

        assertEquals(1, jobs.gameDataRefreshes.size());
    }

    /**
     * Verifies a recent hashes file does not request a GameData Refresh.
     *
     * @throws Exception if fixture setup or bootstrap unexpectedly fails
     */
    @Test
    void freshHashesFileSchedulesNoGameDataRefresh() throws Exception {
        Path dataDirectory = Files.createDirectory(tempDir.resolve("data"));
        copyGameData(dataDirectory);
        writeFreshHashes(dataDirectory);
        RecordingBackgroundJobs jobs = new RecordingBackgroundJobs();

        new AppBootstrap(tempDir.resolve("executable"), dataDirectory, jobs).bootstrap();

        assertTrue(jobs.gameDataRefreshes.isEmpty());
    }

    /**
     * Verifies bootstrap consults and schedules one application-owned GameData
     * Refresh rather than reconstructing work from the resolved directory.
     *
     * @throws Exception if fixture setup or bootstrap unexpectedly fails
     */
    @Test
    void scheduledGameDataRefreshIsSameInstanceConsultedForFreshness() throws Exception {
        Path dataDirectory = Files.createDirectory(tempDir.resolve("data"));
        copyGameData(dataDirectory);
        RecordingBackgroundJobs jobs = new RecordingBackgroundJobs();
        RecordingFreshnessChecks freshnessChecks = new RecordingFreshnessChecks();

        new AppBootstrap(
                tempDir.resolve("executable"),
                dataDirectory,
                jobs,
                freshnessChecks).bootstrap();

        assertSame(freshnessChecks.consultedRefresh, jobs.gameDataRefreshes.getFirst());
    }

    /**
     * Verifies bootstrap opens one module with resolved data and canonical current-Roster
     * Ships before publishing complete application state.
     *
     * @throws Exception if fixture setup or bootstrap unexpectedly fails
     */
    @Test
    void opensOwnedArtworkAfterLoadingAdmirals() throws Exception {
        Path dataDirectory = Files.createDirectory(tempDir.resolve("data"));
        copyGameData(dataDirectory);
        writeMultipleAdmiralsFixture(dataDirectory);
        AtomicReference<ShipArtwork> opened = new AtomicReference<>();
        AtomicReference<List<Ship>> initialShips = new AtomicReference<>();

        new AppBootstrap(tempDir.resolve("executable"), dataDirectory,
                new RecordingBackgroundJobs(), new RecordingFreshnessChecks(),
                (directory, data, rosterShips) -> {
                    assertNull(opened.get(), "bootstrap must open Ship Artwork only once");
                    assertEquals(dataDirectory, directory);
                    assertThrows(IllegalStateException.class, App::shipArtwork);
                    initialShips.set(List.copyOf(rosterShips));
                    for (Ship ship : rosterShips) {
                        assertSame(data.ship(ship.getName()), ship);
                    }
                    ShipArtwork artwork = ShipArtwork.open(directory, data, List.of());
                    opened.set(artwork);
                    return artwork;
                }).bootstrap();

        assertSame(opened.get(), App.shipArtwork());
        assertEquals(Set.of("Class F Shuttle", "Danube Runabout", "U.S.S. Enterprise"),
                initialShips.get().stream().map(Ship::getName).collect(Collectors.toSet()));
    }

    /**
     * Verifies GameData Refresh metadata failures cannot prevent already-readable
     * application data from starting.
     *
     * @throws Exception if fixture setup unexpectedly fails
     */
    @Test
    void gameDataFreshnessFailureRemainsNonfatal() throws Exception {
        Path dataDirectory = Files.createDirectory(tempDir.resolve("data"));
        copyGameData(dataDirectory);
        RecordingBackgroundJobs jobs = new RecordingBackgroundJobs();
        AppBootstrap bootstrap = new AppBootstrap(
                tempDir.resolve("executable"),
                dataDirectory,
                jobs,
                new FailingFreshnessChecks());

        assertDoesNotThrow(bootstrap::bootstrap);

        assertEquals(dataDirectory, App.dataDir());
        assertTrue(jobs.gameDataRefreshes.isEmpty());
        assertNotNull(App.shipArtwork());
    }

    /**
     * Verifies optional unreadable legacy artwork cannot prevent startup or alter
     * the preserved rollback archive.
     *
     * @throws Exception if fixture setup unexpectedly fails
     */
    @Test
    void corruptLegacyArtworkRemainsUntouchedAndStartupSucceeds() throws Exception {
        Path dataDirectory = Files.createDirectory(tempDir.resolve("data"));
        copyGameData(dataDirectory);
        copyResource("/admirals/existing-admirals.xml", dataDirectory.resolve("admirals.xml"));
        writeFreshHashes(dataDirectory);
        Path cacheFile = Files.writeString(dataDirectory.resolve("icons.zip"), "not a zip archive");
        RecordingBackgroundJobs jobs = new RecordingBackgroundJobs();
        AppBootstrap bootstrap = new AppBootstrap(tempDir.resolve("executable"), dataDirectory,
                jobs, new RecordingFreshnessChecks(),
                (directory, data, rosterShips) -> ShipArtwork.open(directory, data, List.of()));

        assertDoesNotThrow(bootstrap::bootstrap);

        assertEquals("not a zip archive", Files.readString(cacheFile));
        assertEquals(dataDirectory, App.dataDir());
        assertNotNull(App.shipArtwork());
    }

    /**
     * Verifies missing required Ships data aborts startup before state publication
     * or job scheduling.
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

        assertTrue(jobs.gameDataRefreshes.isEmpty());
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
     * @param destination  filesystem path receiving the fixture
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
     * Writes a recent hash manifest for GameData Refresh freshness tests.
     *
     * @param dataDirectory directory receiving {@code hashes.md5}
     * @throws IOException if the manifest cannot be written
     */
    private void writeFreshHashes(Path dataDirectory) throws IOException {
        Files.writeString(dataDirectory.resolve("hashes.md5"), "ships.csv=current");
    }

    /**
     * Writes two Admirals whose current Rosters overlap while one additional Ship
     * appears only in usage history.
     *
     * @param dataDirectory directory receiving {@code admirals.xml}
     * @throws IOException if the fixture cannot be written
     */
    private void writeMultipleAdmiralsFixture(Path dataDirectory) throws IOException {
        Files.writeString(
                dataDirectory.resolve("admirals.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<admirals>"
                        + "<admiral prioritizeActive=\"true\">"
                        + "<name>First Admiral</name><faction>Federation</faction>"
                        + "<active>Class F Shuttle</active>"
                        + "<onetime>U.S.S. Enterprise</onetime>"
                        + "<usage><entry><key>I.K.S. Bortas</key><value>7</value></entry></usage>"
                        + "</admiral>"
                        + "<admiral prioritizeActive=\"false\">"
                        + "<name>Second Admiral</name><faction>Klingon</faction>"
                        + "<active>Class F Shuttle</active>"
                        + "<maintenance>Danube Runabout</maintenance>"
                        + "<onetime>U.S.S. Enterprise</onetime>"
                        + "</admiral>"
                        + "</admirals>");
    }

    /**
     * Records requested work without starting threads or touching the network.
     */
    private static final class RecordingBackgroundJobs implements AppBootstrap.BackgroundJobs {

        private final List<GameDataRefresh> gameDataRefreshes = new ArrayList<GameDataRefresh>();

        /**
         * Records the exact GameData Refresh scheduled by bootstrap.
         *
         * @param refresh scheduled application-owned refresh
         */
        @Override
        public void scheduleGameDataRefresh(GameDataRefresh refresh) {
            gameDataRefreshes.add(refresh);
        }

    }

    /**
     * Records the GameData Refresh whose policy bootstrap consults.
     */
    private static final class RecordingFreshnessChecks implements AppBootstrap.FreshnessChecks {

        private GameDataRefresh consultedRefresh;

        /**
         * Records and marks due the exact refresh whose policy bootstrap consults.
         *
         * @param refresh application-owned refresh under test
         * @return always {@code true} so scheduling can be observed
         */
        @Override
        public boolean isGameDataRefreshDue(GameDataRefresh refresh) {
            consultedRefresh = refresh;
            return true;
        }

    }

    /**
     * Simulates unreadable optional GameData Refresh metadata at startup.
     */
    private static final class FailingFreshnessChecks implements AppBootstrap.FreshnessChecks {

        /**
         * Simulates unreadable GameData Refresh freshness metadata.
         *
         * @param refresh application-owned refresh under test
         * @return never returns
         * @throws IOException always
         */
        @Override
        public boolean isGameDataRefreshDue(GameDataRefresh refresh) throws IOException {
            throw new IOException("simulated unreadable GameData freshness metadata");
        }

    }
}
