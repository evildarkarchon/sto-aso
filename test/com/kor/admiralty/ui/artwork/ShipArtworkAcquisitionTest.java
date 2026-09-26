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

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Tests acquisition coordination through opening, lookup and visible artwork. */
class ShipArtworkAcquisitionTest {
    @TempDir Path directory;

    /** Racing lookups must join startup work, and later aliases reuse the completed source. */
    @Test
    void concurrentLookupsAndLateAliasReuseStartupSource() throws Exception {
        Ship startup = ship("racing-image");
        Ship concurrent = ship("racing image");
        Ship late = ship("racing_image");
        List<Consumer<BufferedImage>> requests = new ArrayList<>();
        try (ShipArtwork artwork = new ShipArtwork(directory,
                GameData.builder().ships(List.of(startup, concurrent, late)).build(), List.of(startup),
                ShipArtworkAcquisitionTest::resource, (name, done) -> requests.add(done))) {
            var barrier = new java.util.concurrent.CyclicBarrier(3);
            try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var first = workers.submit(() -> {
                    barrier.await();
                    return artwork.forShip(startup, ShipArtwork.Presentation.SPECIFIC);
                });
                var second = workers.submit(() -> {
                    barrier.await();
                    return artwork.forShip(concurrent, ShipArtwork.Presentation.SPECIFIC);
                });
                barrier.await();
                ImageIcon initial = first.get();
                ImageIcon joined = second.get();
                assertEquals(1, requests.size());
                requests.getFirst().accept(source());
                ImageIcon later = artwork.forShip(late, ShipArtwork.Presentation.SPECIFIC);
                SwingUtilities.invokeAndWait(() -> {
                    assertEquals(0xFFFF00FF, pixel(initial));
                    assertEquals(0xFFFF00FF, pixel(joined));
                    assertEquals(0xFFFF00FF, pixel(later));
                });
                assertEquals(1, requests.size());
            }
        }
    }

    /** Shutdown must abandon queued requests even when active work calls back afterward. */
    @Test
    void closePreventsQueuedStarts() {
        List<Ship> ships = java.util.stream.IntStream.range(0, 5)
                .mapToObj(i -> ship("closing-" + i)).toList();
        List<Consumer<BufferedImage>> requests = new ArrayList<>();
        try (ShipArtwork artwork = new ShipArtwork(directory, GameData.builder().ships(ships).build(),
                ships, ShipArtworkAcquisitionTest::resource, (name, done) -> requests.add(done))) {
            artwork.close();
            requests.getFirst().accept(source());
            requests.get(1).accept(null);
            requests.get(2).accept(null);
            assertEquals(3, requests.size());
        }
    }

    /** A cancelled adapter start must not escape lookup or strand already queued work. */
    @Test
    void cancelledStartDrainsQueueAndInterruptedCompletionPreservesInterrupt() throws Exception {
        List<Ship> ships = java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> ship("terminal-" + i)).toList();
        List<Consumer<BufferedImage>> requests = new ArrayList<>();
        try (ShipArtwork artwork = new ShipArtwork(directory, GameData.builder().ships(ships).build(),
                ships, ShipArtworkAcquisitionTest::resource, (name, done) -> {
                    requests.add(done);
                    if (name.equals("terminal_3.png")) {
                        throw new java.util.concurrent.CancellationException();
                    }
                    if (name.equals("terminal_4.png")) {
                        throw new IllegalStateException("Scripted start failure");
                    }
                })) {
            ImageIcon last = artwork.forShip(ships.get(5), ShipArtwork.Presentation.SPECIFIC);
            Thread.currentThread().interrupt();
            try {
                assertDoesNotThrow(() -> requests.getFirst().accept(null));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                // Clear only this test's deliberately introduced interrupt before invoking Swing.
                Thread.interrupted();
            }
            assertEquals(6, requests.size());
            requests.getLast().accept(source());
            SwingUtilities.invokeAndWait(() -> assertEquals(0xFFFF00FF, pixel(last)));
        }
    }

    /** Held responses expose the limit and out-of-order completion must release exactly one slot. */
    @Test
    void boundsRequestsAndDrainsAfterSuccessAndFailure() throws Exception {
        List<Ship> ships = java.util.stream.IntStream.range(0, 7)
                .mapToObj(i -> ship("bounded-" + i)).toList();
        List<Consumer<BufferedImage>> requests = new ArrayList<>();
        try (ShipArtwork artwork = new ShipArtwork(directory, GameData.builder().ships(ships).build(),
                ships.subList(0, 5), ShipArtworkAcquisitionTest::resource,
                (name, done) -> requests.add(done))) {
            artwork.forShip(ships.get(5), ShipArtwork.Presentation.GENERIC);
            assertEquals(3, requests.size());
            ImageIcon last = artwork.forShip(ships.get(6), ShipArtwork.Presentation.SPECIFIC);
            requests.get(1).accept(source());
            assertEquals(4, requests.size());
            requests.get(0).accept(null);
            assertEquals(5, requests.size());
            requests.get(0).accept(source());
            assertEquals(5, requests.size(), "Duplicate completion must not free another slot");
            requests.get(2).accept(null);
            assertEquals(6, requests.size());
            requests.get(5).accept(source());
            requests.get(3).accept(null);
            requests.get(4).accept(null);
            SwingUtilities.invokeAndWait(() -> assertEquals(0xFFFF00FF, pixel(last)));
            assertEquals(6, requests.size(), "Generic-only demand must not enter the queue");
        }
    }

    /** Distinct canonical Ships sharing a remote name must receive one acquisition's pixels. */
    @Test
    void startupAndOnDemandShareRemoteIdentity() throws Exception {
        Ship first = ship("shared-image");
        Ship second = ship("shared image");
        List<Consumer<BufferedImage>> requests = new ArrayList<>();
        try (ShipArtwork artwork = new ShipArtwork(directory,
                GameData.builder().ships(List.of(first, second)).build(), List.of(first),
                ShipArtworkAcquisitionTest::resource, (name, done) -> requests.add(done))) {
            ImageIcon initial = artwork.forShip(first, ShipArtwork.Presentation.SPECIFIC);
            ImageIcon demanded = artwork.forShip(second, ShipArtwork.Presentation.SPECIFIC);
            ImageIcon generic = artwork.forShip(second, ShipArtwork.Presentation.GENERIC);
            int fallback = pixel(generic);
            assertEquals(1, requests.size());
            requests.getFirst().accept(source());
            SwingUtilities.invokeAndWait(() -> {
                assertEquals(0xFFFF00FF, pixel(initial));
                assertEquals(0xFFFF00FF, pixel(demanded));
                assertEquals(fallback, pixel(generic));
            });
        }
    }

    /** Constructs canonical fixture facts with no bundled specific source. */
    private static Ship ship(String name) {
        return new ShipImpl(ShipFaction.Federation, Tier.Tier1, Rarity.Common, Role.None,
                name, 0, 0, 0, RuleType.All.rewardBonus(0), "");
    }

    private static java.io.InputStream resource(String name) {
        return ShipArtworkAcquisitionTest.class.getResourceAsStream(
                "/com/kor/admiralty/ui/resources/" + name);
    }

    /** Supplies recognizable decoded remote pixels without network access. */
    private static BufferedImage source() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                image.setRGB(x, y, 0xFFFF00FF);
            }
        }
        return image;
    }

    private static int pixel(ImageIcon icon) {
        return ((BufferedImage) icon.getImage()).getRGB(32, 32);
    }
}
