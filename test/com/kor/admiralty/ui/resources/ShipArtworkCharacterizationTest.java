/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.resources;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.RuleType;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;
import com.kor.admiralty.ui.artwork.ShipArtwork;
import com.kor.admiralty.ui.artwork.ShipArtworkTestFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.kor.admiralty.ui.resources.ShipArtworkBaseline.*;
import static org.junit.jupiter.api.Assertions.*;

/** Retains the recorded artwork pixels through the canonical Ship Artwork seam. */
class ShipArtworkCharacterizationTest {

    @TempDir
    Path directory;

    /**
     * Capture output archives remain untouched because the module uses a separate temporary directory.
     *
     * @throws IOException if fixture or capture I/O fails
     */
    @Test
    void captureDestinationDoesNotBecomeArtworkState() throws IOException {
        byte[] existing = {1, 2, 3, 4};
        Path legacy = Files.write(directory.resolve("icons.zip"), existing);
        Path versioned = Files.write(directory.resolve("ship-artwork-v2.zip"), existing);

        ShipArtworkBaseline.main(new String[]{directory.toString()});

        assertArrayEquals(existing, Files.readAllBytes(legacy));
        assertArrayEquals(existing, Files.readAllBytes(versioned));
        assertTrue(Files.isRegularFile(directory.resolve("specific-smooth.png")));
    }

    /**
     * Checks every canonical faction, role, and rarity combination against the fixed generic atlas.
     *
     * @throws IOException if a checked-in image cannot be read
     */
    @Test
    void genericArtworkMatchesRecordedPixels() throws IOException {
        try (ShipArtworkBaseline.BaselineSet baseline = open(directory, read("source.png"))) {
            assertAtlas("generic.png", ShipArtwork.Presentation.GENERIC, baseline);
        }
    }

    /**
     * Checks the public module's smooth-scaled specific pixels for all combinations.
     * The retained specific.png recorded a direct helper that skipped production source scaling.
     *
     * @throws IOException if a checked-in image cannot be read
     */
    @Test
    void specificArtworkMatchesRecordedPixels() throws IOException {
        try (ShipArtworkBaseline.BaselineSet baseline = open(directory, read("source.png"))) {
            assertAtlas("specific-smooth.png", ShipArtwork.Presentation.SPECIFIC, baseline);
        }
    }

    /**
     * Checks the bundled source lookup and smooth scaling through the owned module.
     *
     * @throws IOException if a checked-in image cannot be read
     */
    @Test
    void bundledSpecificArtworkMatchesRecordedPixels() throws IOException {
        try (ShipArtworkBaseline.BaselineSet baseline = open(directory, read("source.png"))) {
            assertPixels(read("bundled-shuttle.png"),
                    baseline.artwork().forShip(baseline.shuttle(), ShipArtwork.Presentation.SPECIFIC));
        }
    }

    /**
     * A missing specific image stays immediately usable with the canonical generic pixels.
     *
     * @throws IOException if the checked-in generic atlas cannot be read
     */
    @Test
    void missingSpecificArtworkUsesGenericPixels() throws IOException {
        Ship missing = ship("Absent Characterization Ship", ShipFaction.Romulan, Role.Sci, Rarity.VeryRare);
        try (ShipArtwork artwork = ShipArtworkTestFixture.offline(directory, List.of(missing))) {
            assertTile(read("generic.png"), ShipFaction.Romulan, Role.Sci, Rarity.VeryRare,
                    artwork.forShip(missing, ShipArtwork.Presentation.SPECIFIC));
        }
    }

    /**
     * Generic One-Time presentation ignores available bundled specific pixels.
     *
     * @throws IOException if a checked-in image cannot be read
     */
    @Test
    void oneTimePresentationIgnoresSpecificPixelsEvenWhenAvailable() throws IOException {
        try (ShipArtworkBaseline.BaselineSet baseline = open(directory, read("source.png"))) {
            Ship shuttle = baseline.shuttle();
            ImageIcon specific = baseline.artwork().forShip(shuttle, ShipArtwork.Presentation.SPECIFIC);
            ImageIcon generic = baseline.artwork().forShip(shuttle, ShipArtwork.Presentation.GENERIC);
            assertPixels(read("bundled-shuttle.png"), specific);
            assertTile(read("generic.png"), ShipFaction.Federation, Role.Smc, Rarity.Common, generic);
            assertSame(generic, baseline.artwork().forShip(shuttle, ShipArtwork.Presentation.GENERIC));
        }
    }

    /**
     * Reads each fixed atlas once and checks all 210 canonical presentation combinations.
     *
     * @throws IOException if the selected atlas cannot be read
     */
    private static void assertAtlas(String file, ShipArtwork.Presentation presentation,
                                    ShipArtworkBaseline.BaselineSet baseline) throws IOException {
        BufferedImage atlas = read(file);
        for (ShipFaction faction : FACTIONS) {
            for (Role role : ROLES) {
                for (Rarity rarity : RARITIES) {
                    assertTile(atlas, faction, role, rarity,
                            baseline.artwork().forShip(baseline.ship(faction, role, rarity), presentation));
                }
            }
        }
    }

    /** Creates one canonical Ship whose unavailable icon must use the generic fallback. */
    private static Ship ship(String name, ShipFaction faction, Role role, Rarity rarity) {
        return new ShipImpl(faction, Tier.Tier6, rarity, role, name, 0, 0, 0,
                RuleType.All.rewardBonus(0), "");
    }

    /** Compares one fixed atlas tile without rebuilding its expected composition. */
    private static void assertTile(BufferedImage atlas, ShipFaction faction, Role role, Rarity rarity,
                                   ImageIcon actual) {
        assertPixels(atlas.getSubimage(column(faction, role) * SIZE,
                RARITIES.indexOf(rarity) * SIZE, SIZE, SIZE), actual);
    }

    /** Paints a stable read-only handle, then checks its dimensions and every ARGB pixel. */
    private static void assertPixels(BufferedImage expected, ImageIcon actual) {
        assertEquals(64, actual.getIconWidth());
        assertEquals(64, actual.getIconHeight());
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            actual.paintIcon(null, graphics, 0, 0);
        } finally {
            graphics.dispose();
        }
        assertArrayEquals(expected.getRGB(0, 0, SIZE, SIZE, null, 0, SIZE),
                image.getRGB(0, 0, SIZE, SIZE, null, 0, SIZE));
    }

    /** Loads a checked-in PNG and fails clearly when a baseline is missing or unreadable. */
    private static BufferedImage read(String name) throws IOException {
        var resource = ShipArtworkCharacterizationTest.class.getResource("/ship-artwork/composition/" + name);
        assertNotNull(resource, "Missing recorded baseline: " + name);
        BufferedImage image = ImageIO.read(resource);
        assertNotNull(image, "Unreadable recorded baseline: " + name);
        return image;
    }
}
