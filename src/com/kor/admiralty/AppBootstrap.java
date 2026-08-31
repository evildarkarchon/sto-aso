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

import static com.kor.admiralty.Globals.FILENAME_SHIPCACHE;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.kor.admiralty.beans.Admirals;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.io.AdmiralsStore;
import com.kor.admiralty.io.AdmiralsStoreException;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.io.GameDataLoadException;
import com.kor.admiralty.ui.resources.IconCache;
import com.kor.admiralty.ui.workers.UpdateDataFiles;

/**
 * Loads application state in one explicit order before any Swing frame is constructed.
 */
public final class AppBootstrap {

    private static final Logger LOGGER = Logger.getLogger(AppBootstrap.class.getName());
    private static final FreshnessChecks FILE_FRESHNESS_CHECKS = new FreshnessChecks() {
        @Override
        public boolean areDataFilesStale(Path dataDirectory) throws IOException {
            return UpdateDataFiles.isStale(dataDirectory);
        }

        @Override
        public boolean isIconCacheStale(IconCache iconCache) {
            return iconCache.isStale();
        }
    };

    private final Path candidateExecutableDirectory;
    private final Path workingDirectory;
    private final BackgroundJobs backgroundJobs;
    private final FreshnessChecks freshnessChecks;

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
        this(candidateExecutableDirectory, workingDirectory, backgroundJobs, FILE_FRESHNESS_CHECKS);
    }

    /**
     * Creates startup orchestration with a replaceable optional-refresh metadata boundary.
     *
     * @param candidateExecutableDirectory directory containing the running jar, EXE, or classes
     * @param workingDirectory             process working directory used as the development fallback
     * @param backgroundJobs               scheduler used after all application data has loaded successfully
     * @param freshnessChecks              optional refresh-metadata checks
     * @throws NullPointerException if any dependency is null
     */
    AppBootstrap(
            Path candidateExecutableDirectory,
            Path workingDirectory,
            BackgroundJobs backgroundJobs,
            FreshnessChecks freshnessChecks) {
        this.candidateExecutableDirectory = Objects.requireNonNull(
                candidateExecutableDirectory,
                "candidateExecutableDirectory");
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory");
        this.backgroundJobs = Objects.requireNonNull(backgroundJobs, "backgroundJobs");
        this.freshnessChecks = Objects.requireNonNull(freshnessChecks, "freshnessChecks");
    }

    /**
     * Resolves and loads all application state, then publishes it atomically through {@link App}.
     *
     * @throws AppBootstrapException if GameData or Admirals cannot be loaded completely
     */
    public void bootstrap() throws AppBootstrapException {
        Path dataDirectory = resolveDataDirectory();
        try {
            GameData gameData = GameData.load(dataDirectory);
            AdmiralsStore admiralsStore = new AdmiralsStore();
            Admirals admirals = admiralsStore.loadOrCreate(dataDirectory, gameData);
            IconCache iconCache = new IconCache(dataDirectory);
            iconCache.load();

            // Production schedulers may run immediately, so publish all shared application state first.
            App.initialize(gameData, admirals, dataDirectory, admiralsStore, iconCache);
            boolean dataFilesStale = areDataFilesStale(dataDirectory);
            boolean iconCacheStale = isIconCacheStale(iconCache);
            if (dataFilesStale) {
                backgroundJobs.scheduleDataFileUpdate(dataDirectory);
            }
            if (iconCacheStale) {
                for (Ship ship : admirals.getCurrentRosterShipTypes()) {
                    backgroundJobs.scheduleIconDownload(ship);
                }
            }
        } catch (GameDataLoadException | AdmiralsStoreException cause) {
            throw new AppBootstrapException("Unable to load application data from " + dataDirectory, cause);
        }
    }

    /**
     * Checks GameData freshness without making optional metadata a startup requirement.
     *
     * @param dataDirectory directory containing the hash manifest
     * @return whether a background data update should be scheduled
     */
    private boolean areDataFilesStale(Path dataDirectory) {
        try {
            return freshnessChecks.areDataFilesStale(dataDirectory);
        } catch (IOException | SecurityException cause) {
            LOGGER.log(Level.WARNING, "Unable to inspect GameData freshness; startup will skip this refresh.", cause);
            return false;
        }
    }

    /**
     * Checks Icon Cache freshness without making optional timestamp bookkeeping a startup requirement.
     *
     * @param iconCache loaded derived Icon Cache
     * @return whether current-Roster Ship icons should be refreshed
     */
    private boolean isIconCacheStale(IconCache iconCache) {
        try {
            return freshnessChecks.isIconCacheStale(iconCache);
        } catch (UncheckedIOException | SecurityException cause) {
            LOGGER.log(Level.WARNING, "Unable to inspect Icon Cache freshness; startup will skip this refresh.", cause);
            return false;
        }
    }

    /**
     * Applies ADR-0001 using {@code ships.csv} as the executable-directory marker.
     *
     * @return executable directory when it contains the marker, otherwise the working directory
     */
    private Path resolveDataDirectory() {
        if (Files.isRegularFile(candidateExecutableDirectory.resolve(FILENAME_SHIPCACHE))) {
            return candidateExecutableDirectory;
        }
        return workingDirectory;
    }

    /**
     * Boundary for startup work that must run asynchronously in production and synchronously record in tests.
     */
    public interface BackgroundJobs {

        /**
         * Schedules download-only refresh of GameData files beneath a directory.
         *
         * @param dataDirectory directory receiving downloaded files
         */
        void scheduleDataFileUpdate(Path dataDirectory);

        /**
         * Schedules download and composition of one current-Roster Ship type's icon.
         *
         * @param ship canonical current-Roster Ship whose icon may need downloading
         */
        void scheduleIconDownload(Ship ship);
    }

    /**
     * Boundary for optional freshness metadata that can fail independently of readable application data.
     */
    interface FreshnessChecks {

        /**
         * Reports whether GameData files need a background refresh.
         *
         * @param dataDirectory directory containing the hash manifest
         * @return whether the refresh should be scheduled
         * @throws IOException if manifest metadata cannot be inspected
         */
        boolean areDataFilesStale(Path dataDirectory) throws IOException;

        /**
         * Reports whether current-Roster Ship icons need a background refresh.
         *
         * @param iconCache loaded derived Icon Cache
         * @return whether icon refreshes should be scheduled
         * @throws UncheckedIOException if cache metadata cannot be inspected or touched
         */
        boolean isIconCacheStale(IconCache iconCache);
    }
}
