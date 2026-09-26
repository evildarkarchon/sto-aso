/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.artwork;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.io.GameData;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Opens real Ship Artwork for presentation tests without a remote transport. */
public final class ShipArtworkTestFixture {
    private ShipArtworkTestFixture() { }

    /**
     * Opens artwork for exactly the canonical Ships supplied by the test.
     * Missing remote images complete as failures without using the network.
     *
     * @param directory temporary artwork directory
     * @param ships canonical Ships to include in test GameData
     * @return an artwork lifetime the test must close
     */
    public static ShipArtwork offline(Path directory, Collection<? extends Ship> ships) {
        return offline(directory, GameData.builder().ships(ships).build());
    }

    /**
     * Opens artwork for an existing GameData while enforcing offline acquisition.
     *
     * @param directory temporary artwork directory
     * @param gameData GameData containing every Ship that the test will render
     * @return an artwork lifetime the test must close
     */
    public static ShipArtwork offline(Path directory, GameData gameData) {
        return scripted(directory, gameData, (name, completed) -> completed.accept(null));
    }

    /**
     * Opens artwork with a deterministic source callback for live presentation tests.
     * The callback must return immediately and complete every request eventually.
     *
     * @param directory temporary artwork directory
     * @param ships canonical Ships to include in test GameData
     * @param acquisition callback receiving the source name and completion
     * @return an artwork lifetime the test must close
     */
    public static ShipArtwork scripted(Path directory, Collection<? extends Ship> ships,
            BiConsumer<String, Consumer<BufferedImage>> acquisition) {
        return scripted(directory, GameData.builder().ships(ships).build(), acquisition);
    }

    /**
     * Opens artwork with an existing GameData and deterministic source callback.
     * The callback must return immediately and complete every request eventually.
     *
     * @param directory temporary artwork directory
     * @param gameData GameData containing every Ship that the test will render
     * @param acquisition callback receiving the source name and completion
     * @return an artwork lifetime the test must close
     */
    public static ShipArtwork scripted(Path directory, GameData gameData,
            BiConsumer<String, Consumer<BufferedImage>> acquisition) {
        return new ShipArtwork(directory, gameData, List.of(),
                name -> ShipArtworkTestFixture.class.getResourceAsStream(
                        "/com/kor/admiralty/ui/resources/" + name), acquisition);
    }
}
