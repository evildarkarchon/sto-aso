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
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns immediate Ship Artwork and live presentation for one loaded GameData instance. */
public final class ShipArtwork implements AutoCloseable {
    /** Explicit card presentation; generic artwork never requests a Ship-specific source. */
    public enum Presentation { GENERIC, SPECIFIC }

    private final ArtworkComposition composition;
    private final BiConsumer<String, Consumer<BufferedImage>> acquisition;
    private final Supplier<Instant> now;
    private final Map<Ship, EnumMap<Presentation, ImageIcon>> handles = new IdentityHashMap<>();
    private final Map<Ship, BufferedImage> bundled = new IdentityHashMap<>();
    private final Map<String, Object> pending = new HashMap<>();
    private final Map<String, RemoteImage> acquired = new HashMap<>();
    private final Map<String, Retry> retries = new HashMap<>();
    private final ArrayDeque<String> queued = new ArrayDeque<>();
    private int active;
    private boolean draining;
    private boolean closed;
    private Runnable closeAcquisition = () -> {
        // Scripted and offline adapters own no transport resources.
    };

    /** Keeps source pixels and their successful acquisition time under the same remote identity. */
    private record RemoteImage(BufferedImage pixels, Instant succeededAt) { }

    /** In-process failure state is deliberately independent of successful freshness. */
    private record Retry(int failures, Instant dueAt) { }

    /**
     * Opens the same lifetime with an internal bundled-resource reader, allowing broken-package
     * failures to be exercised without altering the application classpath. The reader is not retained.
     */
    ShipArtwork(Path dataDirectory, GameData gameData, Collection<? extends Ship> initialRosterShips,
                Function<String, InputStream> resources) {
        this(dataDirectory, gameData, initialRosterShips, resources, (name, completed) -> {
            // Internal offline opening completes missing sources without making a network request.
            completed.accept(null);
        });
    }

    /** Opens the production acquisition path with an internal HTTP client for scripted responses. */
    ShipArtwork(Path dataDirectory, GameData gameData, Collection<? extends Ship> initialRosterShips,
                Function<String, InputStream> resources, HttpClient http) {
        this(dataDirectory, gameData, initialRosterShips, resources, new GithubArtwork(http)::acquire);
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
        this(dataDirectory, gameData, initialRosterShips, resources, acquisition, Instant::now);
    }

    /** Supplies controllable time for remote freshness and retry decisions inside this lifetime. */
    ShipArtwork(Path dataDirectory, GameData gameData, Collection<? extends Ship> initialRosterShips,
                Function<String, InputStream> resources,
                BiConsumer<String, Consumer<BufferedImage>> acquisition, Supplier<Instant> now) {
        this.acquisition = Objects.requireNonNull(acquisition, "acquisition");
        this.now = Objects.requireNonNull(now, "now");
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
     * Opens immediate artwork before Swing views are constructed, acquiring missing sources asynchronously.
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
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        try {
            ShipArtwork artwork = new ShipArtwork(dataDirectory, gameData, initialRosterShips,
                    ShipArtwork::resource, http);
            artwork.closeAcquisition = http::shutdownNow;
            return artwork;
        } catch (RuntimeException | Error failure) {
            http.shutdownNow();
            throw failure;
        }
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
        ArtworkHandle handle = (ArtworkHandle) presentations.get(presentation);
        if (handle == null) {
            BufferedImage pixels = composition.generic(ship);
            if (presentation == Presentation.SPECIFIC) {
                RemoteImage remote = acquired.get(ship.getIconName());
                if (bundled.containsKey(ship)) {
                    pixels = bundled.get(ship);
                } else if (remote != null) {
                    pixels = composition.specific(ship, remote.pixels());
                }
            }
            handle = new ArtworkHandle(pixels);
            // Publish the stable identity before an adapter is allowed to complete synchronously.
            presentations.put(presentation, handle);
        }
        if (presentation == Presentation.SPECIFIC && !bundled.containsKey(ship)) {
            request(ship.getIconName(), false);
        }
        return handle;
    }

    /**
     * Joins remote source work while keeping each canonical Ship's composition independent.
     * The caller must hold this lifetime's monitor to serialize demand with completion.
     */
    private void request(String name, boolean force) {
        RemoteImage remote = acquired.get(name);
        Retry retry = retries.get(name);
        // A failed explicit refresh must remain retryable even if the prior success was recent.
        if (!force && retry == null && remote != null
                && now.get().isBefore(remote.succeededAt().plus(Duration.ofDays(7)))) {
            return;
        }
        if (pending.containsKey(name)) {
            return;
        }
        if (!force && retry != null && now.get().isBefore(retry.dueAt())) {
            return;
        }
        pending.put(name, new Object());
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
                Object attempt = pending.get(name);
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
    private synchronized void finish(String name, Object attempt, BufferedImage source) {
        if (pending.get(name) != attempt) {
            return;
        }
        pending.remove(name);
        active--;
        try {
            if (!closed && source != null) {
                acquired.put(name, new RemoteImage(source, now.get()));
                retries.remove(name);
                // Refresh every existing presentation of this source, including earlier requesters.
                handles.forEach((ship, presentations) -> {
                    ArtworkHandle handle = (ArtworkHandle) presentations.get(Presentation.SPECIFIC);
                    if (handle != null && !bundled.containsKey(ship) && ship.getIconName().equals(name)) {
                        complete(ship, handle, source);
                    }
                });
            } else if (!closed) {
                Retry previous = retries.get(name);
                int failures = previous == null ? 1 : Math.min(3, previous.failures() + 1);
                int minutes = switch (failures) {
                    case 1 -> 1;
                    case 2 -> 5;
                    default -> 30;
                };
                retries.put(name, new Retry(failures, now.get().plus(Duration.ofMinutes(minutes))));
                System.getLogger(ShipArtwork.class.getName()).log(System.Logger.Level.WARNING,
                        "Cannot acquire Ship artwork: " + name + "; retry available in " + minutes + " minute(s)");
            }
        } finally {
            // Every terminal outcome releases capacity even if composition fails.
            drain();
        }
    }

    /**
     * Requests operator refresh, bypassing freshness and backoff but still coalescing active work.
     * This internal tooling entry point validates all canonical Ships before scheduling anything;
     * it throws the same argument/lifetime exceptions as lookup and never refreshes bundled sources.
     */
    synchronized void refreshOnline(Collection<? extends Ship> ships) {
        Objects.requireNonNull(ships, "ships");
        ships.forEach(this::requireCanonical);
        if (closed) {
            throw new IllegalStateException("Ship Artwork is closed");
        }
        ships.stream().filter(ship -> !bundled.containsKey(ship)).map(Ship::getIconName)
                .distinct().forEach(name -> request(name, true));
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

    /** Opens only packaged artwork; remote acquisition is handled separately. */
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
        // Preserve already-returned pixels while aborting optional transport work without waiting.
        closed = true;
        queued.clear();
        pending.clear();
        closeAcquisition.run();
    }
}
