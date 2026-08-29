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

import java.nio.file.Path;
import java.util.Objects;

import com.kor.admiralty.beans.Admirals;
import com.kor.admiralty.io.GameData;

/**
 * Transitional process-wide access to application state while UI panels are migrated to constructor injection.
 * State is published once by {@link AppBootstrap} before any frame is created.
 */
public final class App {

    private static volatile State current;

    private App() {
    }

    /**
     * Returns the current reference data after application bootstrap.
     *
     * @return the bootstrapped GameData
     * @throws IllegalStateException if bootstrap has not completed
     */
    public static GameData gameData() {
        return state().gameData;
    }

    /**
     * Returns the current Admirals after application bootstrap.
     *
     * @return the bootstrapped Admirals
     * @throws IllegalStateException if bootstrap has not completed
     */
    public static Admirals admirals() {
        return state().admirals;
    }

    /**
     * Returns the directory used for all application data files.
     *
     * @return the resolved bootstrapped data directory
     * @throws IllegalStateException if bootstrap has not completed
     */
    public static Path dataDir() {
        return state().dataDirectory;
    }

    /**
     * Atomically publishes complete application state exactly once.
     *
     * @param gameData loaded reference data
     * @param admirals loaded and attached Admirals
     * @param dataDirectory resolved application data directory
     * @throws IllegalStateException if state has already been published
     */
    static synchronized void initialize(GameData gameData, Admirals admirals, Path dataDirectory) {
        if (current != null) {
            throw new IllegalStateException("App has already been bootstrapped");
        }
        current = new State(gameData, admirals, dataDirectory);
    }

    /**
     * Restores process-start state for isolated tests of the static transitional seam.
     */
    static synchronized void resetForTesting() {
        current = null;
    }

    /**
     * Returns the atomically published application state.
     *
     * @return complete application state
     * @throws IllegalStateException if bootstrap has not completed
     */
    private static State state() {
        State state = current;
        if (state == null) {
            throw new IllegalStateException("App has not been bootstrapped");
        }
        return state;
    }

    /**
     * Groups values so readers can never observe a partially published bootstrap.
     */
    private static final class State {

        private final GameData gameData;
        private final Admirals admirals;
        private final Path dataDirectory;

        /**
         * Captures one complete immutable set of application-level references.
         */
        private State(GameData gameData, Admirals admirals, Path dataDirectory) {
            this.gameData = Objects.requireNonNull(gameData, "gameData");
            this.admirals = Objects.requireNonNull(admirals, "admirals");
            this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        }
    }
}
