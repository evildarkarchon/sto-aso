/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.artwork;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.io.GameData;

import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Owns immediate Ship Artwork and live presentation for one loaded GameData instance. */
public final class ShipArtwork implements AutoCloseable {
    /** Explicit card presentation; generic artwork never requests a Ship-specific source. */
    public enum Presentation { GENERIC, SPECIFIC }

    private final ArtworkComposition composition;
    private final BiConsumer<String, Consumer<BufferedImage>> acquisition;
    private final Map<Ship, EnumMap<Presentation, ImageIcon>> handles = new IdentityHashMap<>();
    private final Map<Ship, BufferedImage> bundled = new IdentityHashMap<>();
    private final Map<String, List<Consumer<BufferedImage>>> pending = new HashMap<>();
    private final Map<String, BufferedImage> acquired = new HashMap<>();
    private final ArrayDeque<String> queued = new ArrayDeque<>();
    private int active;
    private boolean draining;
    private boolean closed;

    /**
     * Opens the same lifetime with an internal bundled-resource reader, allowing broken-package
     * failures to be exercised without altering the application classpath. The reader is not retained.
     */
    ShipArtwork(Path dataDirectory, GameData gameData, Collection<? extends Ship> initialRosterShips,
                Function<String, InputStream> resources) {
        this(dataDirectory, gameData, initialRosterShips, resources, (name, completed) -> {
            // Offline opening has no acquisition transport until the later transport stage.
            completed.accept(null);
        });
    }

    /**
     * Supplies the internal asynchronous source boundary. Requests must return immediately;
     * Completion supplies decoded pixels on any thread, with null indicating any terminal
     * failure, cancellation or interruption. Adapters must signal termination even on failure;
     * synchronous start exceptions are treated as failure. Interrupt status is left untouched.
     * Neither the adapter nor its completion protocol is exposed to artwork callers.
     */
    ShipArtwork(Path dataDirectory, GameData gameData, Collection<? extends Ship> initialRosterShips,
                Function<String, InputStream> resources,
                BiConsumer<String, Consumer<BufferedImage>> acquisition) {
        this.acquisition = Objects.requireNonNull(acquisition, "acquisition");
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
        var presentations = handles.get(ship);
        ImageIcon existing = presentations.get(presentation);
        if (existing != null) {
            return existing;
        }
        ArtworkHandle handle = new ArtworkHandle(
                presentation == Presentation.SPECIFIC && bundled.containsKey(ship)
                        ? bundled.get(ship) : composition.generic(ship));
        // Publish the stable identity before an adapter is allowed to complete synchronously.
        presentations.put(presentation, handle);
        if (presentation == Presentation.SPECIFIC && !bundled.containsKey(ship)) {
            request(ship.getIconName(), source -> complete(ship, handle, source));
        }
        return handle;
    }

    /**
     * Joins remote source work while keeping each canonical Ship's composition independent.
     * The caller must hold this lifetime's monitor to serialize demand with completion.
     */
    private void request(String name, Consumer<BufferedImage> completed) {
        if (acquired.containsKey(name)) {
            completed.accept(acquired.get(name));
            return;
        }
        List<Consumer<BufferedImage>> waiters = pending.get(name);
        if (waiters != null) {
            waiters.add(completed);
            return;
        }
        waiters = new ArrayList<>();
        waiters.add(completed);
        pending.put(name, waiters);
        queued.addLast(name);
        drain();
    }

    /**
     * Starts at most three remote attempts while the caller holds this lifetime's monitor.
     * Synchronous callbacks must not recurse through the queue.
     */
    private void drain() {
        if (draining) {
            return;
        }
        draining = true;
        try {
            while (!closed && active < 3 && !queued.isEmpty()) {
                String name = queued.removeFirst();
                List<Consumer<BufferedImage>> attempt = pending.get(name);
                active++;
                try {
                    acquisition.accept(name, source -> finish(name, attempt, source));
                } catch (RuntimeException failure) {
                    // Optional transport startup, including cancellation, cannot strand the queue.
                    finish(name, attempt, null);
                }
            }
        } finally {
            draining = false;
        }
    }

    /** Completes one source for all waiting presentations, ignoring repeated adapter callbacks. */
    private synchronized void finish(String name, List<Consumer<BufferedImage>> attempt, BufferedImage source) {
        if (pending.get(name) != attempt) {
            return;
        }
        pending.remove(name);
        active--;
        try {
            if (!closed && source != null) {
                acquired.put(name, source);
                attempt.forEach(completed -> completed.accept(source));
            }
        } finally {
            // Every terminal outcome releases capacity even if composition fails.
            drain();
        }
    }

    /** Composes privately, then publishes only on the EDT while this lifetime remains open. */
    private void complete(Ship ship, ArtworkHandle handle, BufferedImage source) {
        if (source == null) {
            return;
        }
        BufferedImage pixels = composition.specific(ship, source);
        SwingUtilities.invokeLater(() -> {
            synchronized (ShipArtwork.this) {
                // A queued completion must not advance handles after shutdown has returned.
                if (!closed) {
                    handle.replace(pixels);
                }
            }
        });
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

    /** Closes this lifetime and rejects queued completions; repeated calls have no additional effect. */
    @Override
    public synchronized void close() {
        // Preserve already-returned pixels; this stage owns no workers or writable archives.
        closed = true;
        queued.clear();
        pending.clear();
    }
}
