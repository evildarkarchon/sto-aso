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

import com.kor.admiralty.beans.Admirals;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.io.*;
import com.kor.admiralty.ui.artwork.ShipArtwork;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.kor.admiralty.Globals.FILENAME_SHIPCACHE;

/**
 * Loads application state in one explicit order before any Swing frame is constructed.
 */
public final class AppBootstrap {

    private static final Logger LOGGER = Logger.getLogger(AppBootstrap.class.getName());
    private static final FreshnessChecks FILE_FRESHNESS_CHECKS = new FreshnessChecks() {
        /**
         * Delegates startup policy to the application-owned GameData Refresh.
         *
         * @param refresh refresh whose manifest freshness is being consulted
         * @return whether the refresh is due
         * @throws IOException if freshness metadata cannot be inspected
         */
        @Override
        public boolean isGameDataRefreshDue(GameDataRefresh refresh) throws IOException {
            return refresh.isDue();
        }
    };

    private final Path candidateExecutableDirectory;
    private final Path workingDirectory;
    private final BackgroundJobs backgroundJobs;
    private final FreshnessChecks freshnessChecks;
    private final ArtworkOpener artworkOpener;

    /**
     * Creates startup orchestration for two candidate data directories and a background-work boundary.
     *
     * @param candidateExecutableDirectory directory containing the running jar, EXE, or classes
     * @param workingDirectory             process working directory used as the development fallback
     * @param backgroundJobs               scheduler used after all application data has loaded successfully
     */
    public AppBootstrap(
            Path candidateExecutableDirectory,
            Path workingDirectory,
            BackgroundJobs backgroundJobs) {
        this(candidateExecutableDirectory, workingDirectory, backgroundJobs,
                FILE_FRESHNESS_CHECKS, ShipArtwork::open);
    }

    /**
     * Creates startup orchestration with a replaceable GameData Refresh metadata boundary.
     *
     * @param candidateExecutableDirectory directory containing the running jar, EXE, or classes
     * @param workingDirectory             process working directory used as the development fallback
     * @param backgroundJobs               scheduler used after all application data has loaded successfully
     * @param freshnessChecks              optional GameData Refresh metadata check
     * @throws NullPointerException if any dependency is null
     */
    AppBootstrap(
            Path candidateExecutableDirectory,
            Path workingDirectory,
            BackgroundJobs backgroundJobs,
            FreshnessChecks freshnessChecks) {
        this(candidateExecutableDirectory, workingDirectory, backgroundJobs,
                freshnessChecks, ShipArtwork::open);
    }

    /**
     * Supplies the opening boundary used to verify ordered startup without external acquisition.
     *
     * @param candidateExecutableDirectory directory containing the running application
     * @param workingDirectory process working directory used as the fallback
     * @param backgroundJobs optional GameData Refresh scheduler
     * @param freshnessChecks optional GameData Refresh freshness check
     * @param artworkOpener opens the single application-owned artwork lifetime
     * @throws NullPointerException if any dependency is null
     */
    AppBootstrap(
            Path candidateExecutableDirectory,
            Path workingDirectory,
            BackgroundJobs backgroundJobs,
            FreshnessChecks freshnessChecks,
            ArtworkOpener artworkOpener) {
        this.candidateExecutableDirectory = Objects.requireNonNull(
                candidateExecutableDirectory,
                "candidateExecutableDirectory");
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory");
        this.backgroundJobs = Objects.requireNonNull(backgroundJobs, "backgroundJobs");
        this.freshnessChecks = Objects.requireNonNull(freshnessChecks, "freshnessChecks");
        this.artworkOpener = Objects.requireNonNull(artworkOpener, "artworkOpener");
    }

    /**
     * Resolves GameData and Admirals, opens one Ship Artwork lifetime with the current
     * Roster Ship types, then publishes complete state through {@link App}.
     *
     * @throws AppBootstrapException if GameData or Admirals cannot be loaded completely
     * @throws IllegalStateException if a required bundled composition resource is unavailable
     */
    public void bootstrap() throws AppBootstrapException {
        Path dataDirectory = resolveDataDirectory();
        GameDataRefresh gameDataRefresh = new GameDataRefresh(dataDirectory);
        try {
            GameData gameData = GameData.load(dataDirectory);
            AdmiralsStore admiralsStore = new AdmiralsStore();
            Admirals admirals = admiralsStore.loadOrCreate(dataDirectory, gameData);
            ShipArtwork shipArtwork = Objects.requireNonNull(artworkOpener.open(
                    dataDirectory, gameData, admirals.getCurrentRosterShipTypes()), "shipArtwork");

            // Production schedulers may run immediately, so publish all shared application state first.
            try {
                App.initialize(gameData, admirals, dataDirectory, admiralsStore, shipArtwork);
            } catch (RuntimeException | Error failure) {
                // Publication can reject a repeated bootstrap; do not orphan the new lifetime.
                try {
                    shipArtwork.close();
                } catch (RuntimeException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
                throw failure;
            }
            boolean gameDataRefreshDue = isGameDataRefreshDue(gameDataRefresh);
            if (gameDataRefreshDue) {
                backgroundJobs.scheduleGameDataRefresh(gameDataRefresh);
            }
        } catch (GameDataLoadException | AdmiralsStoreException cause) {
            throw new AppBootstrapException("Unable to load application data from " + dataDirectory, cause);
        }
    }

    /**
     * Checks GameData freshness without making optional metadata a startup requirement.
     *
     * @param refresh application-owned refresh whose policy is being consulted
     * @return whether a background GameData Refresh should be scheduled
     */
    private boolean isGameDataRefreshDue(GameDataRefresh refresh) {
        try {
            return freshnessChecks.isGameDataRefreshDue(refresh);
        } catch (IOException | SecurityException cause) {
            LOGGER.log(Level.WARNING, "Unable to inspect GameData freshness; startup will skip this refresh.", cause);
            return false;
        }
    }

    /**
     * Resolves GameData beside the executable, in the working directory, or in a
     * repository's {@code data} directory, using {@code ships.csv} as the marker.
     *
     * @return the first marked directory, or the working directory so missing
     * data still produces a normal GameData load error
     */
    private Path resolveDataDirectory() {
        if (Files.isRegularFile(candidateExecutableDirectory.resolve(FILENAME_SHIPCACHE))) {
            return candidateExecutableDirectory;
        }
        if (Files.isRegularFile(workingDirectory.resolve(FILENAME_SHIPCACHE))) {
            return workingDirectory;
        }
        Path developmentDataDirectory = workingDirectory.resolve("data");
        // IDE launches use the repository root as CWD, while bundled CSVs live in data/.
        if (Files.isRegularFile(developmentDataDirectory.resolve(FILENAME_SHIPCACHE))) {
            return developmentDataDirectory;
        }
        return workingDirectory;
    }

    /** Boundary for optional GameData Refresh work after application state is published. */
    public interface BackgroundJobs {

        /**
         * Schedules the application-owned GameData Refresh instance already
         * consulted by bootstrap.
         *
         * @param refresh exact GameData Refresh instance due for background work
         */
        void scheduleGameDataRefresh(GameDataRefresh refresh);
    }

    /**
     * Boundary for GameData Refresh metadata that can fail independently of readable application data.
     */
    interface FreshnessChecks {

        /**
         * Reports whether GameData files need a background refresh.
         *
         * @param refresh application-owned refresh whose policy is being consulted
         * @return whether the refresh should be scheduled
         * @throws IOException if manifest metadata cannot be inspected
         */
        boolean isGameDataRefreshDue(GameDataRefresh refresh) throws IOException;
    }

    /** Opens Ship Artwork after data and Admirals are ready, before application publication. */
    @FunctionalInterface
    interface ArtworkOpener {

        /**
         * Opens one module and receives the exact canonical current-Roster Ship types.
         *
         * @param dataDirectory resolved application data directory
         * @param gameData loaded canonical reference data
         * @param initialRosterShips Ship types in current Rosters
         * @return owned Ship Artwork lifetime
         */
        ShipArtwork open(Path dataDirectory, GameData gameData,
                         Collection<? extends Ship> initialRosterShips);
    }
}
