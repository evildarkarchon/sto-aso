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

    private final Path candidateExecutableDirectory;
    private final Path workingDirectory;
    private final BackgroundJobs backgroundJobs;

    /**
     * Creates startup orchestration for two candidate data directories and a background-work boundary.
     *
     * @param candidateExecutableDirectory directory containing the running jar, EXE, or classes
     * @param workingDirectory process working directory used as the development fallback
     * @param backgroundJobs scheduler used after all application data has loaded successfully
     */
    public AppBootstrap(
            Path candidateExecutableDirectory,
            Path workingDirectory,
            BackgroundJobs backgroundJobs) {
        this.candidateExecutableDirectory = Objects.requireNonNull(
                candidateExecutableDirectory,
                "candidateExecutableDirectory");
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory");
        this.backgroundJobs = Objects.requireNonNull(backgroundJobs, "backgroundJobs");
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
            boolean dataFilesStale = UpdateDataFiles.isStale(dataDirectory);
            IconCache iconCache = new IconCache(dataDirectory);
            iconCache.load();
            boolean iconCacheStale = iconCache.isStale();

            // Production schedulers may run immediately, so publish all shared application state first.
            App.initialize(gameData, admirals, dataDirectory, admiralsStore, iconCache);
            if (dataFilesStale) {
                backgroundJobs.scheduleDataFileUpdate(dataDirectory);
            }
            if (iconCacheStale) {
                for (Ship ship : gameData.ships()) {
                    if (ship.isOwned()) {
                        backgroundJobs.scheduleIconDownload(ship);
                    }
                }
            }
        } catch (GameDataLoadException | AdmiralsStoreException | IOException cause) {
            throw new AppBootstrapException("Unable to load application data from " + dataDirectory, cause);
        } catch (UncheckedIOException cause) {
            throw new AppBootstrapException("Unable to inspect application data in " + dataDirectory, cause);
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
         * Schedules download and composition of one owned Ship's icon.
         *
         * @param ship owned Ship whose icon may need downloading
         */
        void scheduleIconDownload(Ship ship);
    }
}
