/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.resources;

import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.ShipFaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

import static com.kor.admiralty.ui.resources.ShipArtworkBaseline.*;
import static org.junit.jupiter.api.Assertions.*;

/** Freezes visible pixels at the current public artwork boundary for the later migration. */
class ShipArtworkCharacterizationTest {

    @TempDir
    Path directory;

    /** Enumerates all 210 presentation combinations, including unclassified values. */
    static Stream<Arguments> presentations() {
        return FACTIONS.stream().flatMap(faction -> ROLES.stream().flatMap(role ->
                RARITIES.stream().map(rarity -> Arguments.of(faction, role, rarity))));
    }

    /** Records generic pixels, including the current absence of rarity decoration. */
    @ParameterizedTest
    @MethodSource("presentations")
    void genericArtworkMatchesRecordedPixels(ShipFaction faction, Role role, Rarity rarity) throws IOException {
        ImageIcon actual = new GenericShipIconFactory().getIcon("unused.png", faction, role, rarity, false);
        assertTile("generic.png", faction, role, rarity, actual);
    }

    /** Records scaling and the background, source, role, then rarity composition order. */
    @ParameterizedTest
    @MethodSource("presentations")
    void specificArtworkMatchesRecordedPixels(ShipFaction faction, Role role, Rarity rarity) throws IOException {
        ImageIcon actual = ActualShipIconFactory.buildIcon(read("source.png"), faction, role, rarity);
        assertTile("specific.png", faction, role, rarity, actual);
    }

    /** Records the bundled lookup and smooth source scaling through the caller-facing factory. */
    @Test
    void bundledSpecificArtworkMatchesRecordedPixels() throws IOException {
        ActualShipIconFactory factory = new ActualShipIconFactory(new IconCache(directory));
        ImageIcon actual = factory.getIcon("Class_F_Shuttle.png", ShipFaction.Federation,
                Role.Smc, Rarity.Common, true);
        assertPixels(read("bundled-shuttle.png"), actual);
    }

    /** Captures generic fallback for a missing specific image without invoking acquisition. */
    @Test
    void missingSpecificArtworkUsesGenericPixels() throws IOException {
        ActualShipIconFactory factory = new ActualShipIconFactory(new IconCache(directory));
        ImageIcon actual = factory.getIcon("__absent_characterization_ship__.png", ShipFaction.Romulan,
                Role.Sci, Rarity.VeryRare, true);
        assertTile("generic.png", ShipFaction.Romulan, Role.Sci, Rarity.VeryRare, actual);
    }

    /** Captures cache precedence and the deliberate generic presentation for One-Time Ships. */
    @Test
    void oneTimePresentationIgnoresSpecificPixelsEvenWhenCached() throws IOException {
        IconCache cache = new IconCache(directory);
        ImageIcon cached = new ImageIcon(read("bundled-shuttle.png"));
        cache.put("Class_F_Shuttle.png", cached);
        ActualShipIconFactory factory = new ActualShipIconFactory(cache);
        assertSame(cached, factory.getIcon("Class_F_Shuttle.png", ShipFaction.Federation,
                Role.Smc, Rarity.Common, true));
        assertTile("generic.png", ShipFaction.Federation, Role.Smc, Rarity.Common,
                factory.getIcon("Class_F_Shuttle.png", ShipFaction.Federation, Role.Smc, Rarity.Common, false));
    }

    /** Compares one fixed atlas tile without regenerating the expected composition. */
    private static void assertTile(String file, ShipFaction faction, Role role, Rarity rarity,
                                   ImageIcon actual) throws IOException {
        BufferedImage atlas = read(file);
        assertPixels(atlas.getSubimage(column(faction, role) * SIZE,
                RARITIES.indexOf(rarity) * SIZE, SIZE, SIZE), actual);
    }

    /** Asserts fixed 64-pixel dimensions and every decoded ARGB pixel, not encoder bytes. */
    private static void assertPixels(BufferedImage expected, ImageIcon actual) {
        assertEquals(64, actual.getIconWidth());
        assertEquals(64, actual.getIconHeight());
        BufferedImage image = (BufferedImage) actual.getImage();
        assertArrayEquals(expected.getRGB(0, 0, 64, 64, null, 0, 64),
                image.getRGB(0, 0, 64, 64, null, 0, 64));
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
