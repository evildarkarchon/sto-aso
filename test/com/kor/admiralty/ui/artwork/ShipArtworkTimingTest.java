/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.artwork;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.enums.*;
import com.kor.admiralty.io.GameData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.ImageIcon;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies scheduled durability and bounded close through lookup and reopened artwork. */
class ShipArtworkTimingTest {
    @TempDir Path directory;
    private final ManualTiming timing = new ManualTiming();
    private final List<Consumer<BufferedImage>> requests = new ArrayList<>();
    private final List<Ship> ships = java.util.stream.IntStream.range(0, 40)
            .mapToObj(i -> (Ship) new ShipImpl(ShipFaction.Federation, Tier.Tier1, Rarity.Common,
                    Role.None, "Timing Ship " + i, 0, 0, 0, RuleType.All.rewardBonus(0), "")).toList();

    /** Each success resets the quiet period; reopening before close observes the completed save. */
    @Test
    void onDemandPersistsTwoSecondsAfterLatestSuccess() {
        try (ShipArtwork artwork = open(List.of())) {
            succeed(artwork, 0);
            timing.advance(1);
            succeed(artwork, 1);
            timing.advance(1);
            assertFalse(Files.exists(directory.resolve("ship-artwork-v2.zip")));
            timing.advance(1);
            assertFresh(0);
            assertFresh(1);
        }
    }

    /** Continuous successes must not keep the archive dirty beyond thirty seconds. */
    @Test
    void continuousSuccessesPersistAtThirtySeconds() {
        try (ShipArtwork artwork = open(List.of())) {
            succeed(artwork, 0);
            for (int i = 1; i < 30; i++) {
                timing.advance(1);
                succeed(artwork, i);
            }
            assertFalse(Files.exists(directory.resolve("ship-artwork-v2.zip")));
            timing.advance(1);
            assertFresh(29);
        }
    }

    /** Startup results are retained together when the batch settles, even after a slow member. */
    @Test
    void startupPersistsOnceWhenBatchCompletes() {
        try (ShipArtwork artwork = open(ships.subList(0, 3))) {
            requests.get(0).accept(source());
            timing.advance(35);
            assertFalse(Files.exists(directory.resolve("ship-artwork-v2.zip")));
            requests.get(1).accept(source());
            requests.get(2).accept(null);
            timing.advance(0);
            assertFresh(0);
            assertFresh(1);
        }
    }

    /** Close retains grace-period successes but neither starts queued work nor accepts new demand. */
    @Test
    void closeRetainsGracePeriodSuccessAndBoundsUnresponsiveWork() {
        ShipArtwork artwork = open(ships.subList(0, 5));
        timing.duringWait = () -> {
            assertThrows(IllegalStateException.class,
                    () -> artwork.forShip(ships.get(5), ShipArtwork.Presentation.SPECIFIC));
            requests.getFirst().accept(source());
        };
        artwork.close();
        assertEquals(3, requests.size(), "Queued work must not start during grace");
        assertEquals(2_000_000_000L, timing.nanos, "Unresponsive work gets one bounded grace");
        assertFresh(0);
        requests.get(1).accept(source());
        artwork.close();
        assertEquals(2_000_000_000L, timing.nanos, "Repeated close must not wait again");
    }

    /** Cancelling a refresh of recent artwork must preserve pixels but make it due next launch. */
    @Test
    void interruptedCloseFlushesSuccessAndMakesUnfinishedRefreshDueAgain() {
        ShipArtwork artwork = open(List.of());
        succeed(artwork, 0);
        artwork.refreshOnline(List.of(ships.getFirst()));
        Thread.currentThread().interrupt();
        try {
            artwork.close();
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(0, timing.nanos, "Interrupted close skips grace");
        } finally {
            // Clear only the interrupt deliberately introduced by this test.
            Thread.interrupted();
        }
        requests.getLast().accept(source());
        List<String> restarted = new ArrayList<>();
        try (ShipArtwork reopened = new ShipArtwork(directory, GameData.builder().ships(ships).build(), List.of(),
                name -> getClass().getResourceAsStream("/com/kor/admiralty/ui/resources/" + name),
                (name, done) -> { restarted.add(name); done.accept(null); },
                () -> Instant.parse("2026-01-01T00:00:00Z"))) {
            ImageIcon icon = reopened.forShip(ships.getFirst(), ShipArtwork.Presentation.SPECIFIC);
            assertEquals(0xFFFF00FF, ((BufferedImage) icon.getImage()).getRGB(32, 32));
            assertEquals(List.of(ships.getFirst().getIconName()), restarted);
        }
    }

    /** Pixels delivered by an interrupted attempt cannot become successful freshness. */
    @Test
    void interruptedCompletionRemainsDueAfterRestart() {
        try (ShipArtwork artwork = open(List.of())) {
            artwork.forShip(ships.getFirst(), ShipArtwork.Presentation.SPECIFIC);
            Thread.currentThread().interrupt();
            try {
                requests.getFirst().accept(source());
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                // Clear only this test's interrupt before reopening and using Swing.
                Thread.interrupted();
            }
        }
        int before = requests.size();
        try (ShipArtwork artwork = open(List.of())) {
            artwork.forShip(ships.getFirst(), ShipArtwork.Presentation.SPECIFIC);
            assertEquals(before + 1, requests.size());
        }
    }

    /** A callback during installation must stay dirty, and disk I/O must not hold the lookup lock. */
    @Test
    void finalFlushIncludesSuccessArrivingDuringEarlierInstallation() {
        var current = new java.util.concurrent.atomic.AtomicReference<ShipArtwork>();
        var installs = new java.util.concurrent.atomic.AtomicInteger();
        ShipArtworkArchive.FileMover mover = (source, target, options) -> {
            if (installs.incrementAndGet() == 1) {
                try {
                    java.util.concurrent.CompletableFuture.runAsync(() -> succeed(current.get(), 1))
                            .get(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new AssertionError("Acquisition must progress during archive installation", failure);
                }
            }
            return Files.move(source, target, options);
        };
        try (ShipArtwork artwork = new ShipArtwork(directory, GameData.builder().ships(ships).build(), List.of(),
                name -> getClass().getResourceAsStream("/com/kor/admiralty/ui/resources/" + name),
                (name, done) -> requests.add(done), () -> Instant.parse("2026-01-01T00:00:00Z"), mover, timing)) {
            current.set(artwork);
            succeed(artwork, 0);
            timing.advance(2);
            assertFresh(0);
        }
        assertFresh(1);
        assertEquals(2, installs.get());
        timing.advance(40);
        assertEquals(2, installs.get(), "Closed timers must not write again");
    }

    /** Inline startup completions cannot expose a partially collected batch to persistence. */
    @Test
    void synchronousStartupInstallsOneCompletedBatch() {
        var installs = new java.util.concurrent.atomic.AtomicInteger();
        try (ShipArtwork artwork = new ShipArtwork(directory, GameData.builder().ships(ships).build(), ships,
                name -> getClass().getResourceAsStream("/com/kor/admiralty/ui/resources/" + name),
                (name, done) -> done.accept(source()), () -> Instant.parse("2026-01-01T00:00:00Z"),
                (source, target, options) -> {
                    installs.incrementAndGet();
                    return Files.move(source, target, options);
                }, timing)) {
            timing.advance(0);
            assertFresh(0);
            assertFresh(39);
            timing.advance(40);
        }
        assertEquals(1, installs.get());
    }

    /** On-demand durability must not publish partial startup results while a startup source is held. */
    @Test
    void onDemandSaveLeavesStartupResultsUntilBatchCompletes() {
        try (ShipArtwork artwork = open(ships.subList(0, 2))) {
            requests.getFirst().accept(source());
            succeed(artwork, 2);
            timing.advance(2);
            assertFresh(2);
            List<String> restarted = new ArrayList<>();
            try (ShipArtwork reopened = new ShipArtwork(directory, GameData.builder().ships(ships).build(), List.of(),
                    name -> getClass().getResourceAsStream("/com/kor/admiralty/ui/resources/" + name),
                    (name, done) -> { restarted.add(name); done.accept(null); })) {
                ImageIcon icon = reopened.forShip(ships.getFirst(), ShipArtwork.Presentation.SPECIFIC);
                assertNotEquals(0xFFFF00FF, ((BufferedImage) icon.getImage()).getRGB(32, 32));
                assertEquals(List.of(ships.getFirst().getIconName()), restarted);
            }
            requests.get(1).accept(source());
            timing.advance(0);
            assertFresh(0);
            assertFresh(1);
        }
    }

    /** A mixed save must retain old pixels with old freshness until refreshed startup artwork commits. */
    @Test
    void incompleteStartupRefreshRetainsPriorDurablePixelsAndFreshness() {
        try (ShipArtwork seed = new ShipArtwork(directory, GameData.builder().ships(ships).build(), List.of(),
                name -> getClass().getResourceAsStream("/com/kor/admiralty/ui/resources/" + name),
                (name, done) -> done.accept(source()), () -> Instant.parse("2025-12-20T00:00:00Z"))) {
            seed.forShip(ships.getFirst(), ShipArtwork.Presentation.SPECIFIC);
        }
        try (ShipArtwork artwork = open(ships.subList(0, 2))) {
            BufferedImage refreshed = source();
            refreshed.setRGB(32, 32, 0xFF00FFFF);
            requests.getFirst().accept(refreshed);
            succeed(artwork, 2);
            timing.advance(2);
            List<String> restarted = new ArrayList<>();
            try (ShipArtwork reopened = new ShipArtwork(directory, GameData.builder().ships(ships).build(), List.of(),
                    name -> getClass().getResourceAsStream("/com/kor/admiralty/ui/resources/" + name),
                    (name, done) -> { restarted.add(name); done.accept(null); },
                    () -> Instant.parse("2026-01-01T00:00:00Z"))) {
                ImageIcon icon = reopened.forShip(ships.getFirst(), ShipArtwork.Presentation.SPECIFIC);
                assertEquals(0xFFFF00FF, ((BufferedImage) icon.getImage()).getRGB(32, 32));
                assertEquals(List.of(ships.getFirst().getIconName()), restarted, "Old artwork remains stale");
            }
            requests.get(1).accept(null);
            timing.advance(0);
        }
    }

    /** Closing during a mixed installation must still flush the startup pixels omitted by that snapshot. */
    @Test
    void closeDuringMixedInstallationRetainsCompletedStartupArtwork() throws Exception {
        var installing = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var grace = new java.util.concurrent.CountDownLatch(1);
        timing.duringWait = grace::countDown;
        ShipArtwork artwork = new ShipArtwork(directory, GameData.builder().ships(ships).build(), ships.subList(0, 2),
                name -> getClass().getResourceAsStream("/com/kor/admiralty/ui/resources/" + name),
                (name, done) -> requests.add(done), () -> Instant.parse("2026-01-01T00:00:00Z"),
                (source, target, options) -> {
                    installing.countDown();
                    try {
                        if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                            throw new AssertionError("Installation was not released");
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                    return Files.move(source, target, options);
                }, timing);
        try {
            requests.getFirst().accept(source());
            succeed(artwork, 2);
            var saving = java.util.concurrent.CompletableFuture.runAsync(() -> timing.advance(2));
            assertTrue(installing.await(5, java.util.concurrent.TimeUnit.SECONDS));
            var closing = java.util.concurrent.CompletableFuture.runAsync(artwork::close);
            assertTrue(grace.await(5, java.util.concurrent.TimeUnit.SECONDS));
            synchronized (artwork) {
                // The scripted wait retains this monitor, so acquiring it observes the completed freeze.
            }
            release.countDown();
            saving.get(5, java.util.concurrent.TimeUnit.SECONDS);
            closing.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertFresh(0);
            assertFresh(2);
        } finally {
            release.countDown();
            artwork.close();
        }
    }

    /** Opens the real module with only acquisition and passage of time controlled. */
    private ShipArtwork open(List<Ship> startup) {
        return new ShipArtwork(directory, GameData.builder().ships(ships).build(), startup,
                name -> getClass().getResourceAsStream("/com/kor/admiralty/ui/resources/" + name),
                (name, done) -> requests.add(done), () -> Instant.parse("2026-01-01T00:00:00Z"),
                Files::move, timing);
    }

    /** Delivers unmistakable source pixels through the acquisition boundary. */
    private void succeed(ShipArtwork artwork, int index) {
        artwork.forShip(ships.get(index), ShipArtwork.Presentation.SPECIFIC);
        requests.getLast().accept(source());
    }

    /** Reopens independently and checks both retained pixels and successful freshness. */
    private void assertFresh(int index) {
        List<String> restarted = new ArrayList<>();
        try (ShipArtwork artwork = new ShipArtwork(directory, GameData.builder().ships(ships).build(), List.of(),
                name -> getClass().getResourceAsStream("/com/kor/admiralty/ui/resources/" + name),
                (name, done) -> { restarted.add(name); done.accept(null); },
                () -> Instant.parse("2026-01-01T00:00:00Z"))) {
            ImageIcon icon = artwork.forShip(ships.get(index), ShipArtwork.Presentation.SPECIFIC);
            assertEquals(0xFFFF00FF, ((BufferedImage) icon.getImage()).getRGB(32, 32));
            assertTrue(restarted.isEmpty(), "Saved successes must remain fresh after restart");
        }
    }

    /** Supplies solid pixels independent of the production composition recipe. */
    private static BufferedImage source() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) image.setRGB(x, y, 0xFFFF00FF);
        return image;
    }

    /** Runs due timer callbacks and monitor waits with no elapsed wall-clock time. */
    private static final class ManualTiming extends ArtworkTiming {
        private long nanos;
        private final List<Task> tasks = new ArrayList<>();
        private Runnable duringWait = () -> { /* No completion is scripted by default. */ };

        @Override long nanoTime() { return nanos; }

        /** Retains a cancellable deadline, mirroring a scheduled executor. */
        @Override Runnable schedule(Runnable action, long delayNanos) {
            Task task = new Task(nanos + delayNanos, action);
            tasks.add(task);
            return () -> tasks.remove(task);
        }

        /** Advances a bounded wait and permits a scripted completion during the grace period. */
        @Override void await(Object monitor, long remainingNanos) throws InterruptedException {
            if (Thread.interrupted()) throw new InterruptedException();
            duringWait.run();
            nanos += remainingNanos;
        }

        /** Drains due callbacks in deadline order, including newly scheduled callbacks. */
        void advance(long seconds) {
            long target = nanos + seconds * 1_000_000_000L;
            while (true) {
                Task next = tasks.stream().filter(task -> task.at <= target)
                        .min(java.util.Comparator.comparingLong(Task::at)).orElse(null);
                if (next == null) break;
                tasks.remove(next);
                nanos = next.at;
                next.action.run();
            }
            nanos = target;
        }

        private record Task(long at, Runnable action) { }
    }
}
