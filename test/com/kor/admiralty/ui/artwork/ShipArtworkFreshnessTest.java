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
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises freshness and recovery through lookup, transport requests and visible pixels. */
class ShipArtworkFreshnessTest {
    @TempDir Path directory;
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    private final List<Request> requests = new ArrayList<>();

    /** Cancellation and interruption leave the image due, and obsolete callbacks cannot make it fresh. */
    @Test
    void cancellationAndInterruptionRemainRetryable() throws Exception {
        Ship ship = ship("cancelled-refresh");
        try (ShipArtwork artwork = new ShipArtwork(directory,
                GameData.builder().ships(List.of(ship)).build(), List.of(),
                name -> getClass().getResourceAsStream("/com/kor/admiralty/ui/resources/" + name),
                (name, done) -> {
                    requests.add(new Request(name, done));
                    if (requests.size() == 1) {
                        throw new java.util.concurrent.CancellationException("Scripted cancellation");
                    }
                }, now::get)) {
            ImageIcon icon = artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
            int fallback = pixel(icon);
            advance(Duration.ofMinutes(1));
            artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
            assertEquals(2, requests.size());
            requests.getFirst().done.accept(source(Color.GREEN));
            Thread.currentThread().interrupt();
            try {
                requests.getLast().done.accept(null);
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                // Clear only the interrupt deliberately introduced by this test before invoking Swing.
                Thread.interrupted();
            }
            SwingUtilities.invokeAndWait(() -> assertEquals(fallback, pixel(icon)));
            advance(Duration.ofMinutes(5).minusSeconds(1));
            artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
            assertEquals(2, requests.size());
            advance(Duration.ofSeconds(1));
            artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
            assertEquals(3, requests.size());
            requests.getLast().done.accept(source(Color.MAGENTA));
            SwingUtilities.invokeAndWait(() -> assertEquals(Color.MAGENTA.getRGB(), pixel(icon)));
        }
    }

    /** Previously returned and newly requested presentations share retained and refreshed source pixels. */
    @Test
    void sharedSourceKeepsAllHandlesCurrentAndLateRequestsImmediatelyShowStalePixels() throws Exception {
        Ship first = ship("shared-freshness");
        Ship second = ship("shared freshness");
        Ship late = ship("shared_freshness");
        try (ShipArtwork artwork = open(first, second, late)) {
            ImageIcon initial = artwork.forShip(first, ShipArtwork.Presentation.SPECIFIC);
            ImageIcon joined = artwork.forShip(second, ShipArtwork.Presentation.SPECIFIC);
            requests.getFirst().done.accept(source(Color.MAGENTA));
            advance(Duration.ofDays(7));
            ImageIcon later = artwork.forShip(late, ShipArtwork.Presentation.SPECIFIC);
            assertEquals(Color.MAGENTA.getRGB(), pixel(later));
            assertEquals(2, requests.size());
            requests.getLast().done.accept(null);
            advance(Duration.ofMinutes(1));
            artwork.forShip(first, ShipArtwork.Presentation.SPECIFIC);
            requests.getLast().done.accept(source(Color.GREEN));
            SwingUtilities.invokeAndWait(() -> {
                assertEquals(Color.GREEN.getRGB(), pixel(initial));
                assertEquals(Color.GREEN.getRGB(), pixel(joined));
                assertEquals(Color.GREEN.getRGB(), pixel(later));
            });
        }
    }

    /** Explicit operator requests bypass backoff and freshness while retaining active coalescing. */
    @Test
    void explicitRefreshBypassesBackoffAndFreshnessAndNewLifetimeRetries() throws Exception {
        Ship ship = ship("forced-refresh");
        try (ShipArtwork artwork = open(ship)) {
            ImageIcon icon = artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
            requests.getFirst().done.accept(null);
            artwork.refreshOnline(List.of(ship));
            assertEquals(2, requests.size());
            artwork.refreshOnline(List.of(ship, ship));
            assertEquals(2, requests.size(), "Explicit refresh still joins active work");
            requests.getLast().done.accept(source(Color.MAGENTA));
            artwork.refreshOnline(List.of(ship));
            assertEquals(3, requests.size(), "Explicit refresh also replaces fresh sources");
            requests.getLast().done.accept(null);
            SwingUtilities.invokeAndWait(() -> assertEquals(Color.MAGENTA.getRGB(), pixel(icon)));
            advance(Duration.ofMinutes(1));
            artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
            assertEquals(4, requests.size(), "Failed explicit refresh retries even if earlier pixels were fresh");
            requests.getLast().done.accept(null);
        }
        try (ShipArtwork restarted = open(ship)) {
            restarted.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
            assertEquals(5, requests.size(), "Backoff must not survive the artwork lifetime");
        }
    }

    /** One shared failed attempt emits one non-modal diagnostic even after duplicate callbacks. */
    @Test
    void coalescedFailureLogsOnce() {
        Ship first = ship("diagnostic-image");
        Ship second = ship("diagnostic image");
        List<java.util.logging.LogRecord> diagnostics = new ArrayList<>();
        var logger = java.util.logging.Logger.getLogger(ShipArtwork.class.getName());
        var handler = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                diagnostics.add(record);
            }

            @Override
            public void flush() {
                // The in-memory diagnostic recorder has nothing to flush.
            }

            @Override
            public void close() {
                // The in-memory diagnostic recorder owns no resources.
            }
        };
        logger.addHandler(handler);
        try (ShipArtwork artwork = open(first, second)) {
            artwork.forShip(first, ShipArtwork.Presentation.SPECIFIC);
            artwork.forShip(second, ShipArtwork.Presentation.SPECIFIC);
            assertEquals(1, requests.size());
            requests.getFirst().done.accept(null);
            requests.getFirst().done.accept(null);
            artwork.forShip(first, ShipArtwork.Presentation.SPECIFIC);
            assertEquals(1, diagnostics.size());
            assertTrue(diagnostics.getFirst().getMessage().contains(first.getIconName()));
        } finally {
            logger.removeHandler(handler);
        }
    }

    /** Identical refreshed pixels extend freshness without invalidating a visible paint owner. */
    @Test
    void unchangedRefreshAdvancesFreshnessWithoutRepainting() throws Exception {
        Ship ship = ship("unchanged-refresh");
        try (ShipArtwork artwork = open(ship)) {
            ImageIcon icon = artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
            requests.getFirst().done.accept(source(Color.MAGENTA));
            Owner owner = new Owner();
            SwingUtilities.invokeAndWait(() -> {
                var graphics = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB).createGraphics();
                try {
                    icon.paintIcon(owner, graphics, 0, 0);
                } finally {
                    graphics.dispose();
                }
                owner.repaints = 0;
            });
            advance(Duration.ofDays(7));
            artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
            assertEquals(2, requests.size());
            requests.getLast().done.accept(source(Color.MAGENTA));
            SwingUtilities.invokeAndWait(() -> {
                assertEquals(Color.MAGENTA.getRGB(), pixel(icon));
                assertEquals(0, owner.repaints);
            });
            advance(Duration.ofDays(7).minusSeconds(1));
            artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
            assertEquals(2, requests.size());
            advance(Duration.ofSeconds(1));
            artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
            assertEquals(3, requests.size());
        }
    }

    /** Failures retain visible pixels and throttle only their image; success restarts the sequence. */
    @Test
    void failuresBackOffIndependentlyAndSuccessResetsTheSequence() throws Exception {
        Ship first = ship("retry-first");
        Ship second = ship("retry-second");
        try (ShipArtwork artwork = open(first, second)) {
            ImageIcon icon = artwork.forShip(first, ShipArtwork.Presentation.SPECIFIC);
            int fallback = pixel(icon);
            requests.getFirst().done.accept(null);
            artwork.forShip(first, ShipArtwork.Presentation.SPECIFIC);
            assertEquals(1, requests.size());
            SwingUtilities.invokeAndWait(() -> assertEquals(fallback, pixel(icon)));
            artwork.forShip(second, ShipArtwork.Presentation.SPECIFIC);
            assertEquals(2, requests.size(), "One failure must not block another source");
            requests.getLast().done.accept(source(Color.CYAN));

            for (int minutes : new int[]{1, 5, 30, 30}) {
                int count = requests.size();
                advance(Duration.ofMinutes(minutes).minusSeconds(1));
                artwork.forShip(first, ShipArtwork.Presentation.SPECIFIC);
                assertEquals(count, requests.size());
                advance(Duration.ofSeconds(1));
                artwork.forShip(first, ShipArtwork.Presentation.SPECIFIC);
                assertEquals(count + 1, requests.size());
                if (count < 5) {
                    requests.getLast().done.accept(null);
                }
            }
            requests.getLast().done.accept(source(Color.MAGENTA));
            SwingUtilities.invokeAndWait(() -> assertEquals(Color.MAGENTA.getRGB(), pixel(icon)));
            advance(Duration.ofDays(7));
            artwork.forShip(first, ShipArtwork.Presentation.SPECIFIC);
            requests.getLast().done.accept(null);
            SwingUtilities.invokeAndWait(() -> assertEquals(Color.MAGENTA.getRGB(), pixel(icon)));
            int count = requests.size();
            advance(Duration.ofSeconds(59));
            artwork.forShip(first, ShipArtwork.Presentation.SPECIFIC);
            assertEquals(count, requests.size());
            advance(Duration.ofSeconds(1));
            artwork.forShip(first, ShipArtwork.Presentation.SPECIFIC);
            assertEquals(count + 1, requests.size(), "Success resets retry to one minute");
            requests.getLast().done.accept(source(Color.GREEN));
            SwingUtilities.invokeAndWait(() -> assertEquals(Color.GREEN.getRGB(), pixel(icon)));
        }
    }

    /** Seven days starts at each successful completion, never at scheduling or another image's success. */
    @Test
    void freshnessBelongsToEachSuccessfulImage() throws Exception {
        Ship first = ship("fresh-first");
        Ship second = ship("fresh-second");
        try (ShipArtwork artwork = open(first, second)) {
            ImageIcon initial = artwork.forShip(first, ShipArtwork.Presentation.SPECIFIC);
            artwork.forShip(second, ShipArtwork.Presentation.SPECIFIC);
            advance(Duration.ofDays(2));
            requests.getFirst().done.accept(source(Color.MAGENTA));
            advance(Duration.ofDays(1));
            requests.get(1).done.accept(source(Color.CYAN));
            advance(Duration.ofDays(6).minusSeconds(1));
            assertSame(initial, artwork.forShip(first, ShipArtwork.Presentation.SPECIFIC));
            artwork.forShip(second, ShipArtwork.Presentation.SPECIFIC);
            assertEquals(2, requests.size());
            advance(Duration.ofSeconds(1));
            artwork.forShip(first, ShipArtwork.Presentation.SPECIFIC);
            artwork.forShip(second, ShipArtwork.Presentation.SPECIFIC);
            assertEquals(3, requests.size());
            assertEquals(first.getIconName(), requests.getLast().name);
            requests.getLast().done.accept(source(Color.GREEN));
            SwingUtilities.invokeAndWait(() -> assertEquals(Color.GREEN.getRGB(), pixel(initial)));
            advance(Duration.ofDays(1));
            artwork.forShip(first, ShipArtwork.Presentation.SPECIFIC);
            artwork.forShip(second, ShipArtwork.Presentation.SPECIFIC);
            assertEquals(4, requests.size());
            assertEquals(second.getIconName(), requests.getLast().name);
        }
    }

    /** Opens the public lifetime with scripted external acquisition and time. */
    private ShipArtwork open(Ship... ships) {
        return new ShipArtwork(directory, GameData.builder().ships(List.of(ships)).build(), List.of(),
                name -> getClass().getResourceAsStream("/com/kor/admiralty/ui/resources/" + name),
                (name, done) -> requests.add(new Request(name, done)), now::get);
    }

    private void advance(Duration duration) {
        now.updateAndGet(time -> time.plus(duration));
    }

    /** Supplies canonical fixture facts without packaged specific artwork. */
    private static Ship ship(String name) {
        return new ShipImpl(ShipFaction.Federation, Tier.Tier1, Rarity.Common, Role.None,
                name, 0, 0, 0, RuleType.All.rewardBonus(0), "");
    }

    /** Supplies recognizable decoded pixels independently of composition. */
    private static BufferedImage source(Color color) {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, 64, 64);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static int pixel(ImageIcon icon) {
        return ((BufferedImage) icon.getImage()).getRGB(32, 32);
    }

    private record Request(String name, Consumer<BufferedImage> done) { }

    /** Records repaint requests while simulating a visible component in headless Swing. */
    private static final class Owner extends JComponent {
        int repaints;

        @Override
        public boolean isShowing() {
            return true;
        }

        @Override
        public void repaint() {
            repaints++;
        }
    }
}
