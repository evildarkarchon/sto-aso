/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.artwork;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.io.GameData;

import javax.swing.ImageIcon;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Owns immediate offline Ship Artwork for one loaded GameData instance. */
public final class ShipArtwork implements AutoCloseable {
    /** Explicit card presentation; generic artwork never requests a Ship-specific source. */
    public enum Presentation { GENERIC, SPECIFIC }

    private final ArtworkComposition composition;
    private final Map<Ship, EnumMap<Presentation, ImageIcon>> handles = new IdentityHashMap<>();
    private final Map<Ship, BufferedImage> bundled = new IdentityHashMap<>();
    private boolean closed;

    /**
     * Opens the same lifetime with an internal bundled-resource reader, allowing broken-package
     * failures to be exercised without altering the application classpath. The reader is not retained.
     */
    ShipArtwork(Path dataDirectory, GameData gameData, Collection<? extends Ship> initialRosterShips,
                Function<String, InputStream> resources) {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Objects.requireNonNull(gameData, "gameData");
        Objects.requireNonNull(initialRosterShips, "initialRosterShips");
        Objects.requireNonNull(resources, "resources");
        for (Ship ship : gameData.ships()) {
            handles.put(ship, new EnumMap<>(Presentation.class));
        }
        // Validate the whole startup request before loading resources or prewarming anything.
        initialRosterShips.forEach(this::requireCanonical);
        composition = new ArtworkComposition(resources);
        // Resolve every optional source now: later lookup must not perform even classpath I/O.
        for (Ship ship : gameData.ships()) {
            try (InputStream input = resources.apply(ship.getIconName())) {
                if (input != null) {
                    BufferedImage source = ImageIO.read(input);
                    if (source != null) {
                        bundled.put(ship, composition.specific(ship, source));
                    }
                }
            } catch (IOException failure) {
                System.getLogger(ShipArtwork.class.getName()).log(System.Logger.Level.WARNING,
                        "Cannot read optional Ship artwork: " + ship.getIconName(), failure);
            }
        }
        initialRosterShips.forEach(ship -> forShip(ship, Presentation.SPECIFIC));
    }

    /**
     * Opens offline artwork before Swing views are constructed.
     * @param dataDirectory already resolved application data directory
     * @param gameData canonical reference data for this lifetime
     * @param initialRosterShips canonical Ship types in the current Roster
     * @return application-owned artwork
     * @throws NullPointerException if an argument or initial Ship is null
     * @throws IllegalArgumentException if an initial Ship is foreign to GameData
     * @throws IllegalStateException if a required bundled composition resource is unavailable
     */
    public static ShipArtwork open(Path dataDirectory, GameData gameData,
                                   Collection<? extends Ship> initialRosterShips) {
        return new ShipArtwork(dataDirectory, gameData, initialRosterShips, ShipArtwork::resource);
    }

    /**
     * Returns stable 64-pixel artwork immediately, without filesystem or network I/O.
     * Safe on any thread, including Swing's event thread. Previously returned handles remain
     * paintable after close, but the closed lifetime no longer accepts lookups.
     * @throws NullPointerException if either argument is null
     * @throws IllegalArgumentException if the Ship is not canonical to this lifetime
     * @throws IllegalStateException if this lifetime is closed
     */
    public synchronized ImageIcon forShip(Ship ship, Presentation presentation) {
        requireCanonical(ship);
        Objects.requireNonNull(presentation, "presentation");
        if (closed) {
            throw new IllegalStateException("Ship Artwork is closed");
        }
        return handles.get(ship)
                .computeIfAbsent(presentation, ignored -> new ArtworkHandle(
                        presentation == Presentation.SPECIFIC && bundled.containsKey(ship)
                                ? bundled.get(ship) : composition.generic(ship)));
    }

    /** Opens only packaged artwork; optional missing sources have no remote fallback in this stage. */
    private static InputStream resource(String name) {
        return ShipArtwork.class.getResourceAsStream("/com/kor/admiralty/ui/resources/" + name);
    }

    /** Checks reference identity because an equal Ship from another GameData is still foreign. */
    private void requireCanonical(Ship ship) {
        Objects.requireNonNull(ship, "ship");
        if (!handles.containsKey(ship)) {
            throw new IllegalArgumentException("Ship does not belong to this GameData: " + ship.getName());
        }
    }

    /** Closes this offline lifetime; repeated calls have no additional effect. */
    @Override
    public synchronized void close() {
        // Preserve already-returned pixels; this stage owns no workers or writable archives.
        closed = true;
    }
}
