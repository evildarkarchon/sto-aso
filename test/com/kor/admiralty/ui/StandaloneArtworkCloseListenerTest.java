/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui;

import com.kor.admiralty.io.GameData;
import com.kor.admiralty.ui.artwork.ShipArtwork;
import com.kor.admiralty.ui.artwork.ShipArtworkTestFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Exercises the standalone window lifetime without constructing a native frame. */
class StandaloneArtworkCloseListenerTest {
    @TempDir
    Path directory;

    /**
     * Closing the window ends the exact artwork lifetime supplied to that root.
     *
     * @throws Exception if the isolated GameData fixture cannot be loaded
     */
    @Test
    void windowClosingClosesOwnedArtworkIdempotently() throws Exception {
        GameData gameData = GameData.load(Path.of("test/resources/gamedata"));
        var ship = gameData.ship("Class F Shuttle");
        ShipArtwork artwork = ShipArtworkTestFixture.offline(directory, gameData);
        StandaloneArtworkCloseListener listener = new StandaloneArtworkCloseListener(artwork);

        artwork.forShip(ship, ShipArtwork.Presentation.GENERIC);
        listener.windowClosing(null);
        assertThrows(IllegalStateException.class,
                () -> artwork.forShip(ship, ShipArtwork.Presentation.GENERIC));
        listener.windowClosing(null);
    }
}
