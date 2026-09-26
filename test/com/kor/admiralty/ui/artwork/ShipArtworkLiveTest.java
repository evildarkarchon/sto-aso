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
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Exercises asynchronous completion through the stable public artwork handle. */
class ShipArtworkLiveTest {
    @TempDir
    Path directory;

    /** Rubber-stamp labels must invalidate their host, without keeping that host alive. */
    @Test
    void rendererOwnersAreCoalescedAndWeaklyHeld() throws Exception {
        AtomicReference<Consumer<BufferedImage>> completion = new AtomicReference<>();
        Ship ship = new ShipImpl(ShipFaction.Federation, Tier.Tier1, Rarity.Common, Role.None,
                "renderer-live", 0, 0, 0, RuleType.All.rewardBonus(0), "");
        try (ShipArtwork artwork = new ShipArtwork(directory, GameData.builder().ships(List.of(ship)).build(),
                List.of(), name -> ShipArtworkLiveTest.class.getResourceAsStream(
                        "/com/kor/admiralty/ui/resources/" + name),
                (name, done) -> completion.set(done))) {
            ImageIcon icon = artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
            Owner host = new Owner();
            AtomicReference<WeakReference<Owner>> abandoned = new AtomicReference<>();
            AtomicReference<WeakReference<Owner>> collectionProbe = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                CellRendererPane pane = new CellRendererPane();
                host.add(pane);
                JPanel renderer = new JPanel();
                JLabel label = new JLabel();
                renderer.add(label);
                pane.add(renderer);
                paint(icon, label);
                paint(icon, renderer);
                host.repaints = 0;
                abandoned.set(paintAbandonedOwner(icon));
                collectionProbe.set(new WeakReference<>(new Owner()));
            });
            // No sleep or native window is needed: only the weakly tracked owner is collectible.
            for (int attempt = 0; attempt < 30 && !abandoned.get().refersTo(null); attempt++) {
                System.gc();
            }
            completion.get().accept(source(Color.CYAN));
            SwingUtilities.invokeAndWait(() -> {
                assertEquals(1, host.repaints);
                assertTrue(host.repaintOnEdt);
                assertEquals(Color.CYAN.getRGB(), pixel(icon));
            });
            // Explicit GC is advisory; distinguish a VM ignoring it from artwork retaining a view.
            assumeTrue(collectionProbe.get().refersTo(null), "VM did not collect the unrelated control owner");
            assertTrue(abandoned.get().refersTo(null), "Artwork retained an unreachable paint owner");
        }
    }

    /** Queued acquisition must preserve the last visible pixels once the lifetime is closed. */
    @Test
    void closingBeforeEventDispatchRejectsCompletion() throws Exception {
        AtomicReference<Consumer<BufferedImage>> completion = new AtomicReference<>();
        Ship ship = new ShipImpl(ShipFaction.Federation, Tier.Tier1, Rarity.Common, Role.None,
                "closed-live", 0, 0, 0, RuleType.All.rewardBonus(0), "");
        try (ShipArtwork artwork = new ShipArtwork(directory, GameData.builder().ships(List.of(ship)).build(),
                List.of(), name -> ShipArtworkLiveTest.class.getResourceAsStream(
                        "/com/kor/admiralty/ui/resources/" + name),
                (name, done) -> completion.set(done))) {
            ImageIcon icon = artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
            int fallback = pixel(icon);
            SwingUtilities.invokeAndWait(() -> {
                completion.get().accept(source(Color.MAGENTA));
                artwork.close();
            });
            SwingUtilities.invokeAndWait(() -> assertEquals(fallback, pixel(icon)));
        }
    }

    /** Drops all strong test references after painting, making owner retention observable. */
    private static WeakReference<Owner> paintAbandonedOwner(ImageIcon icon) {
        Owner owner = new Owner();
        paint(icon, owner);
        return new WeakReference<>(owner);
    }

    /** Completion must wait for the EDT, preserve identity, and repaint each visible owner once. */
    @Test
    void completionReplacesExistingPixelsOnEventThread() throws Exception {
        Ship ship = new ShipImpl(ShipFaction.Federation, Tier.Tier1, Rarity.Common, Role.None,
                "missing-live", 0, 0, 0, RuleType.All.rewardBonus(0), "");
        AtomicReference<Consumer<BufferedImage>> completion = new AtomicReference<>();
        try (ShipArtwork artwork = new ShipArtwork(directory, GameData.builder().ships(List.of(ship)).build(),
                List.of(), name -> ShipArtworkLiveTest.class.getResourceAsStream(
                        "/com/kor/admiralty/ui/resources/" + name),
                (name, done) -> completion.set(done))) {
            ImageIcon icon = artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
            ImageIcon generic = artwork.forShip(ship, ShipArtwork.Presentation.GENERIC);
            int fallback = pixel(icon);
            Owner owner = new Owner();
            Owner hidden = new Owner();
            SwingUtilities.invokeAndWait(() -> {
                paint(icon, owner);
                paint(icon, owner);
                paint(icon, hidden);
                hidden.showing = false;
                owner.repaints = 0;
                hidden.repaints = 0;
                // Completing on a worker while the EDT is occupied exposes premature replacement.
                Thread worker = new Thread(() -> completion.get().accept(source(Color.MAGENTA)));
                worker.start();
                try {
                    worker.join();
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(failure);
                }
                assertEquals(fallback, pixel(icon));
                assertEquals(0, owner.repaints);
            });
            SwingUtilities.invokeAndWait(() -> {
                assertEquals(Color.MAGENTA.getRGB(), pixel(icon));
                assertEquals(fallback, pixel(generic));
                assertSame(icon, artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC));
                assertEquals(1, owner.repaints);
                assertTrue(owner.repaintOnEdt);
                assertEquals(0, hidden.repaints);
            });
        }
    }

    /** Supplies decoded external pixels independently of the artwork composition implementation. */
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

    /** Reads a visible center pixel through the defensive public snapshot. */
    private static int pixel(ImageIcon icon) {
        return ((BufferedImage) icon.getImage()).getRGB(32, 32);
    }

    /** Simulates Swing painting with the actual component passed to the image handle. */
    private static void paint(ImageIcon icon, Component owner) {
        var graphics = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            icon.paintIcon(owner, graphics, 0, 0);
        } finally {
            graphics.dispose();
        }
    }

    /** Models native visibility in headless tests and records public repaint requests. */
    private static final class Owner extends JComponent {
        boolean showing = true;
        int repaints;
        boolean repaintOnEdt;

        @Override
        public boolean isShowing() {
            return showing;
        }

        /** Records the thread on which artwork asks Swing to repaint. */
        @Override
        public void repaint() {
            repaints++;
            repaintOnEdt = SwingUtilities.isEventDispatchThread();
        }
    }
}
