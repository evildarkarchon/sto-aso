/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty;

import com.kor.admiralty.beans.Admirals;
import com.kor.admiralty.io.AdmiralsStore;
import com.kor.admiralty.io.AdmiralsStoreException;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.ui.artwork.ShipArtwork;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Publishes minimal complete application state for UI tests that still cross
 * the transitional static seam.
 */
public final class AppTestFixture {

    private AppTestFixture() {
    }

    /**
     * Replaces any prior test state with a complete application using the supplied
     * GameData and an isolated artwork data directory.
     *
     * @param gameData reference data required by runtime Swing controls
     * @throws AdmiralsStoreException if JAXB cannot be initialized for complete App state
     * @throws IOException if an isolated artwork data directory cannot be created
     */
    public static void initialize(GameData gameData) throws AdmiralsStoreException, IOException {
        Path dataDirectory = Files.createTempDirectory("aso-app-state-");
        initialize(
                gameData,
                new Admirals(gameData),
                dataDirectory,
                new AdmiralsStore(),
                ShipArtwork.open(dataDirectory, gameData, List.of()));
    }

    /**
     * Replaces prior test state with caller-supplied complete application
     * dependencies, including an explicit Admirals projection.
     *
     * @param gameData      reference data required by runtime Swing controls
     * @param admirals      initialized Admirals shown by application-level views
     * @param dataDirectory isolated application-data directory
     * @param admiralsStore initialized persistence dependency
     * @param shipArtwork   isolated application-owned Ship Artwork
     * @throws NullPointerException if an argument is null
     */
    public static void initialize(
            GameData gameData,
            Admirals admirals,
            Path dataDirectory,
            AdmiralsStore admiralsStore,
            ShipArtwork shipArtwork) {
        App.resetForTesting();
        App.initialize(gameData, admirals, dataDirectory, admiralsStore, shipArtwork);
    }

    /**
     * Restores process-start state after a UI test.
     */
    public static void reset() {
        App.resetForTesting();
    }
}
