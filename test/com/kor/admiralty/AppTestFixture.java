/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty;

import java.nio.file.Path;

import com.kor.admiralty.beans.Admirals;
import com.kor.admiralty.io.AdmiralsStore;
import com.kor.admiralty.io.AdmiralsStoreException;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.ui.resources.IconCache;

/**
 * Publishes minimal complete application state for UI tests that still cross the transitional static seam.
 */
public final class AppTestFixture {

    private AppTestFixture() {
    }

    /**
     * Replaces any prior test state with a complete in-memory application using the supplied GameData.
     *
     * @param gameData reference data required by runtime Swing controls
     * @throws AdmiralsStoreException if JAXB cannot be initialized for the required complete App state
     */
    public static void initialize(GameData gameData) throws AdmiralsStoreException {
        App.resetForTesting();
        App.initialize(
                gameData,
                new Admirals(gameData),
                Path.of("."),
                new AdmiralsStore(),
                new IconCache(Path.of(".")));
    }

    /**
     * Restores process-start state after a UI test.
     */
    public static void reset() {
        App.resetForTesting();
    }
}
