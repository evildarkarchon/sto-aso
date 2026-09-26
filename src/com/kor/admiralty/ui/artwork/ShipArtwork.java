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
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Owns immediate Ship Artwork and live presentation for one loaded GameData instance. */
public final class ShipArtwork implements AutoCloseable {
    /** Explicit card presentation; generic artwork never requests a Ship-specific source. */
    public enum Presentation { GENERIC, SPECIFIC }

    private final ArtworkComposition composition;
    private final BiConsumer<String, Consumer<BufferedImage>> acquisition;
    private final Supplier<Instant> now;
    private final ShipArtworkArchive archive;
    private final ArtworkTiming timing;
    private final Map<Ship, EnumMap<Presentation, ImageIcon>> handles = new IdentityHashMap<>();
    private final Map<Ship, BufferedImage> bundled = new IdentityHashMap<>();
    private final Map<Ship, BufferedImage> legacy = new IdentityHashMap<>();
    private final Map<ShipArtworkArchive.LegacyIdentity, BufferedImage> legacyStored = new HashMap<>();
    private LegacyArtworkMigration.Outcome migrationOutcome = LegacyArtworkMigration.Outcome.empty();
    private final Map<String, Object> pending = new HashMap<>();
    private final Map<String, RemoteImage> acquired = new HashMap<>();
    private final Map<ShipArtworkArchive.Identity, BufferedImage> persisted = new HashMap<>();
    private final Map<String, Instant> successfulAt = new HashMap<>();
    private final Set<String> refreshDue = new HashSet<>();
    private final Map<String, Retry> retries = new HashMap<>();
    private final ArrayDeque<String> queued = new ArrayDeque<>();
    private final Set<String> startupPending = new HashSet<>();
    private boolean collectingStartup;
    private boolean startupChanged;
    private ShipArtworkArchive.State startupBaseline;
    private final Set<String> deferredStartupSources = new HashSet<>();
    private int active;
    private boolean draining;
    private boolean archiveChanged;
    private boolean persistenceEnabled = true;
    private long revision;
    private long saveTicket;
    private Runnable cancelSave;
    private Long firstUnsavedSuccess;
    private final Object writer = new Object();
    private final Object shutdown = new Object();
    private boolean closing;
    private boolean closed;
    private Runnable closeAcquisition = () -> {
        // Scripted and offline adapters own no transport resources.
    };

    /** Keeps acquired source pixels separate from each composed presentation identity. */
    private record RemoteImage(BufferedImage pixels) { }

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
        this(dataDirectory, gameData, initialRosterShips, resources, acquisition, now, Files::move);
    }

    /** Supplies the narrow archive-move boundary for installation and quarantine fault tests. */
    ShipArtwork(Path dataDirectory, GameData gameData, Collection<? extends Ship> initialRosterShips,
                Function<String, InputStream> resources,
                BiConsumer<String, Consumer<BufferedImage>> acquisition, Supplier<Instant> now,
                ShipArtworkArchive.FileMover fileMover) {
        this(dataDirectory, gameData, initialRosterShips, resources, acquisition, now, fileMover,
                new ArtworkTiming());
    }

    /**
     * Supplies internal monotonic scheduling and bounded waiting without exposing them to UI callers.
     * Corrupt archives are quarantined before acquisition; failed quarantine disables persistence
     * for this lifetime while keeping immediate artwork and acquisition available.
     */
    ShipArtwork(Path dataDirectory, GameData gameData, Collection<? extends Ship> initialRosterShips,
                Function<String, InputStream> resources,
                BiConsumer<String, Consumer<BufferedImage>> acquisition, Supplier<Instant> now,
                ShipArtworkArchive.FileMover fileMover, ArtworkTiming timing) {
        this.timing = Objects.requireNonNull(timing, "timing");
        this.acquisition = Objects.requireNonNull(acquisition, "acquisition");
        this.now = Objects.requireNonNull(now, "now");
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Objects.requireNonNull(gameData, "gameData");
        Objects.requireNonNull(initialRosterShips, "initialRosterShips");
        Objects.requireNonNull(resources, "resources");
        archive = new ShipArtworkArchive(dataDirectory, fileMover);
        for (Ship ship : gameData.ships()) {
            handles.put(ship, new EnumMap<>(Presentation.class));
        }
        // Validate the whole startup request before loading resources or prewarming anything.
        initialRosterShips.forEach(this::requireCanonical);
        composition = new ArtworkComposition(resources);
        try {
            ShipArtworkArchive.State state = archive.load();
            persisted.putAll(state.artwork());
            legacyStored.putAll(state.legacy());
            successfulAt.putAll(state.freshness());
            refreshDue.addAll(state.refreshDue());
        } catch (IOException failure) {
            // Corrupt derived state must not prevent immediate generic or bundled artwork.
            System.getLogger(ShipArtwork.class.getName()).log(System.Logger.Level.WARNING,
                    "Cannot load persisted Ship Artwork", failure);
            try {
                archive.quarantine();
            } catch (IOException quarantineFailure) {
                // Never let a later success or final flush overwrite unquarantined evidence.
                persistenceEnabled = false;
                System.getLogger(ShipArtwork.class.getName()).log(System.Logger.Level.WARNING,
                        "Cannot quarantine corrupt Ship Artwork", quarantineFailure);
            }
        }
        Map<String, Ship> uniqueLegacyShips = LegacyArtworkMigration.uniqueShips(gameData);
        Set<ShipArtworkArchive.LegacyIdentity> allowedLegacy = new HashSet<>();
        uniqueLegacyShips.values().forEach(ship ->
                allowedLegacy.add(ShipArtworkArchive.LegacyIdentity.from(ship)));
        // A later GameData can remove a Ship or introduce a filename collision; stale pixels
        // must not survive in v2 when they no longer identify this lifetime's canonical Ship.
        if (legacyStored.keySet().removeIf(identity -> !allowedLegacy.contains(identity))) {
            archiveChanged = true;
            revision++;
        }
        LegacyArtworkMigration.Result migration = LegacyArtworkMigration.migrate(
                dataDirectory.resolve("icons.zip"), gameData, persisted.keySet(), legacyStored.keySet());
        migrationOutcome = migration.outcome();
        if (!migration.artwork().isEmpty()) {
            legacyStored.putAll(migration.artwork());
            archiveChanged = true;
            revision++;
        }
        uniqueLegacyShips.values().forEach(ship -> {
            BufferedImage pixels = legacyStored.get(ShipArtworkArchive.LegacyIdentity.from(ship));
            if (pixels != null) legacy.put(ship, pixels);
        });
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
        synchronized (this) {
            startupBaseline = new ShipArtworkArchive.State(persisted, legacyStored, successfulAt, refreshDue);
            // Synchronous adapters may finish before the next startup Ship has been queued.
            collectingStartup = true;
            initialRosterShips.forEach(ship -> forShip(ship, Presentation.SPECIFIC));
            collectingStartup = false;
            saveCompletedStartup();
        }
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
        if (closing || closed) {
            throw new IllegalStateException("Ship Artwork is closed");
        }
        var presentations = handles.get(ship);
        ShipArtworkArchive.Identity identity = ShipArtworkArchive.Identity.from(ship);
        ArtworkHandle handle = (ArtworkHandle) presentations.get(presentation);
        if (handle == null) {
            BufferedImage pixels = composition.generic(ship);
            if (presentation == Presentation.SPECIFIC) {
                RemoteImage remote = acquired.get(ship.getIconName());
                if (bundled.containsKey(ship)) {
                    pixels = bundled.get(ship);
                } else if (remote != null) {
                    pixels = composition.specific(ship, remote.pixels());
                } else if (persisted.containsKey(identity)) {
                    pixels = persisted.get(identity);
                } else if (legacy.containsKey(ship)) {
                    pixels = legacy.get(ship);
                }
            }
            handle = new ArtworkHandle(pixels);
            // Publish the stable identity before an adapter is allowed to complete synchronously.
            presentations.put(presentation, handle);
        }
        if (presentation == Presentation.SPECIFIC && !bundled.containsKey(ship)) {
            request(ship.getIconName(), false, persisted.containsKey(identity));
        }
        return handle;
    }

    /**
     * Joins remote source work while keeping each canonical Ship's composition independent.
     * The caller must hold this lifetime's monitor to serialize demand with completion.
     */
    private void request(String name, boolean force, boolean hasVersionedArtwork) {
        Retry retry = retries.get(name);
        // A failed explicit refresh must remain retryable even if the prior success was recent.
        Instant success = successfulAt.get(name);
        if (!force && retry == null && !refreshDue.contains(name) && hasVersionedArtwork && success != null
                && now.get().isBefore(success.plus(Duration.ofDays(7)))) {
            return;
        }
        if (pending.containsKey(name)) {
            return;
        }
        if (!force && retry != null && now.get().isBefore(retry.dueAt())) {
            return;
        }
        pending.put(name, new Object());
        if (collectingStartup) startupPending.add(name);
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
            while (!closing && !closed && active < 3 && !queued.isEmpty()) {
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
        boolean startup = startupPending.remove(name);
        active--;
        try {
            // An interrupted adapter has not completed a usable acquisition, even if it supplies pixels.
            if (!closed && source != null && !Thread.currentThread().isInterrupted()) {
                Instant succeededAt = now.get();
                acquired.put(name, new RemoteImage(source));
                successfulAt.put(name, succeededAt);
                refreshDue.remove(name);
                retries.remove(name);
                // Persist every canonical presentation sharing this source so identity remains complete.
                handles.keySet().stream().filter(ship -> ship.getIconName().equals(name)).forEach(ship ->
                        persisted.put(ShipArtworkArchive.Identity.from(ship), composition.specific(ship, source)));
                archiveChanged = true;
                revision++;
                if (startup) {
                    startupChanged = true;
                    deferredStartupSources.add(name);
                } else {
                    deferredStartupSources.remove(name);
                    long completedAt = timing.nanoTime();
                    if (firstUnsavedSuccess == null) firstUnsavedSuccess = completedAt;
                    scheduleSave(Math.min(Duration.ofSeconds(2).toNanos(),
                            Math.max(0, Duration.ofSeconds(30).toNanos() - (completedAt - firstUnsavedSuccess))));
                }
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
                // Persist only launch-time eligibility; exponential failure state remains lifetime-local.
                if (successfulAt.containsKey(name)) {
                    refreshDue.add(name);
                    archiveChanged = true;
                    revision++;
                }
                System.getLogger(ShipArtwork.class.getName()).log(System.Logger.Level.WARNING,
                        "Cannot acquire Ship artwork: " + name + "; retry available in " + minutes + " minute(s)");
            }
        } finally {
            // Every terminal outcome releases capacity even if composition fails.
            drain();
            saveCompletedStartup();
            notifyAll();
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
        if (closing || closed) {
            throw new IllegalStateException("Ship Artwork is closed");
        }
        ships.stream().filter(ship -> !bundled.containsKey(ship)).map(Ship::getIconName)
                .distinct().forEach(name -> request(name, true, false));
    }

    /** Returns the read-only legacy migration decisions for operator tooling in this package. */
    synchronized LegacyArtworkMigration.Outcome migrationOutcome() {
        return migrationOutcome;
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
                if (!closing && !closed) {
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

    /** Schedules one background save after every startup source has reached a terminal outcome. */
    private void saveCompletedStartup() {
        if (!collectingStartup && startupPending.isEmpty()) {
            startupBaseline = null;
            deferredStartupSources.clear();
            if (startupChanged) {
                startupChanged = false;
                archiveChanged = true;
                revision++;
                scheduleSave(0);
            }
        }
    }

    /**
     * Keeps incomplete startup results visible in memory but out of on-demand installations.
     * Restoring the launch baseline also keeps prior pixels and their freshness paired until batch commit.
     * The caller holds the lifetime monitor; shutdown deliberately includes every completed result.
     */
    private ShipArtworkArchive.State persistenceSnapshot() {
        if (closed || startupBaseline == null || deferredStartupSources.isEmpty()) {
            return new ShipArtworkArchive.State(persisted, legacyStored, successfulAt, refreshDue);
        }
        var artwork = new HashMap<>(persisted);
        var freshness = new HashMap<>(successfulAt);
        var due = new HashSet<>(refreshDue);
        artwork.keySet().removeIf(identity -> deferredStartupSources.contains(identity.sourceImage()));
        startupBaseline.artwork().forEach((identity, pixels) -> {
            if (deferredStartupSources.contains(identity.sourceImage())) artwork.put(identity, pixels);
        });
        for (String name : deferredStartupSources) {
            freshness.remove(name);
            if (startupBaseline.freshness().containsKey(name)) {
                freshness.put(name, startupBaseline.freshness().get(name));
            }
            due.remove(name);
            if (startupBaseline.refreshDue().contains(name)) due.add(name);
        }
        return new ShipArtworkArchive.State(artwork, legacyStored, freshness, due);
    }

    /** Replaces a pending deadline; stale callbacks cannot overwrite a newer scheduling decision. */
    private void scheduleSave(long delayNanos) {
        if (closing || closed) return;
        if (cancelSave != null) cancelSave.run();
        long ticket = ++saveTicket;
        cancelSave = timing.schedule(() -> flush(ticket), delayNanos);
    }

    /**
     * Serializes complete archive installations, copying state under the lifetime monitor only.
     * A success during disk I/O remains dirty and is saved by its own timer or final close.
     */
    private void flush(long ticket) {
        synchronized (writer) {
            ShipArtworkArchive.State snapshot;
            long savedRevision;
            boolean omittedStartup;
            synchronized (this) {
                if (!persistenceEnabled || !archiveChanged || (ticket != 0 && (closed || ticket != saveTicket))) return;
                snapshot = persistenceSnapshot();
                omittedStartup = !closed && !deferredStartupSources.isEmpty();
                savedRevision = revision;
                firstUnsavedSuccess = null;
            }
            try {
                archive.save(snapshot);
                synchronized (this) {
                    // Close can change snapshot eligibility during I/O; use what this write actually included.
                    if (revision == savedRevision) archiveChanged = omittedStartup;
                }
            } catch (IOException failure) {
                // Optional derived persistence failure must not endanger application shutdown.
                System.getLogger(ShipArtwork.class.getName()).log(System.Logger.Level.WARNING,
                        "Cannot persist Ship Artwork", failure);
            }
        }
    }

    /**
     * Rejects demand, gives active acquisition at most two seconds, then cancels and flushes.
     * Concurrent closes share one shutdown; interruption skips further waiting and is preserved.
     * Disk installation is serialized separately so callbacks can complete during the grace period.
     */
    @Override
    public void close() {
        synchronized (shutdown) {
            synchronized (this) {
                if (closed) return;
                closing = true;
                if (cancelSave != null) cancelSave.run();
                ++saveTicket;
                long began = timing.nanoTime();
                long grace = Duration.ofSeconds(2).toNanos();
                while (active > 0) {
                    long remaining = grace - (timing.nanoTime() - began);
                    if (remaining <= 0) break;
                    try {
                        timing.await(this, remaining);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                // Freeze completion before cancelling transport, whose cancellation may call back inline.
                closed = true;
                // A cancelled explicit refresh can have recent last-known-good pixels; keep it due.
                for (String name : pending.keySet()) {
                    if (successfulAt.containsKey(name)) {
                        refreshDue.add(name);
                        archiveChanged = true;
                        revision++;
                    }
                }
                queued.clear();
                pending.clear();
            }
            closeAcquisition.run();
            flush(0);
            timing.close();
        }
    }
}
