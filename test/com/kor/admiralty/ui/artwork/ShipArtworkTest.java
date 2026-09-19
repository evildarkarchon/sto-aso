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

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises artwork through its creation, lookup and lifetime boundary. */
class ShipArtworkTest {
    @TempDir
    Path directory;

    /** Catches lazy classpath access during lookup, including after startup prewarming. */
    @Test
    void lookupDoesNoResourceIoAndUnreadableOptionalArtworkFallsBack() throws Exception {
        Ship bundled = ship("Class F Shuttle", ShipFaction.Federation, Role.Smc, Rarity.Common);
        Ship broken = ship("broken", ShipFaction.Federation, Role.Smc, Rarity.Common);
        GameData data = GameData.builder().ships(List.of(bundled, broken)).build();
        AtomicBoolean opened = new AtomicBoolean();
        try (ShipArtwork artwork = new ShipArtwork(directory, data, List.of(bundled), name -> {
            assertFalse(opened.get(), "Resource I/O after construction: " + name);
            return name.equals("broken.png") ? new ByteArrayInputStream(new byte[]{1, 2, 3}) : resource(name);
        })) {
            opened.set(true);
            assertPixels(baseline("bundled-shuttle.png"), artwork.forShip(bundled, ShipArtwork.Presentation.SPECIFIC));
            BufferedImage generic = baseline("generic.png").getSubimage(9 * 64, 64, 64, 64);
            assertPixels(generic, artwork.forShip(broken, ShipArtwork.Presentation.SPECIFIC));
            assertPixels(generic, artwork.forShip(bundled, ShipArtwork.Presentation.GENERIC));
            assertPixels(generic, artwork.forShip(broken, ShipArtwork.Presentation.GENERIC));
        }
        try (var entries = java.nio.file.Files.list(directory)) {
            assertEquals(0, entries.count(), "Offline artwork must not create an archive");
        }
    }

    /** Catches opening resources before the entire startup request has been validated. */
    @Test
    void invalidStartupRosterIsRejectedBeforeResourceAccess() {
        Ship ship = ship("Cruiser", ShipFaction.Federation, Role.Eng, Rarity.Epic);
        Ship foreign = ship("Cruiser", ShipFaction.Federation, Role.Eng, Rarity.Epic);
        GameData data = GameData.builder().ships(List.of(ship)).build();
        assertThrows(IllegalArgumentException.class, () -> new ShipArtwork(directory, data,
                List.of(ship, foreign), name -> { throw new AssertionError("Resource opened: " + name); }));
        assertThrows(NullPointerException.class, () -> new ShipArtwork(directory, data,
                java.util.Arrays.asList(ship, null), name -> { throw new AssertionError("Resource opened: " + name); }));
    }

    /** Catches silently accepting broken distributions, including decoder failures. */
    @Test
    void missingOrUnreadableRequiredResourcesFailOpeningWithTheirName() {
        GameData data = GameData.builder().build();
        List<String> required = List.of("lobi.png", "fed_bkg.png", "kdf_bkg.png", "rom_bkg.png", "jh_bkg.png",
                "fed_eng.png", "fed_tac.png", "fed_sci.png", "fed_smc.png",
                "kdf_eng.png", "kdf_tac.png", "kdf_sci.png", "kdf_smc.png",
                "rom_eng.png", "rom_tac.png", "rom_sci.png", "rom_smc.png",
                "jh_eng.png", "jh_tac.png", "jh_sci.png", "jh_smc.png",
                "eng.png", "tac.png", "sci.png", "frame_eng.png", "frame_tac.png", "frame_sci.png",
                "frame_smc.png", "frame_uncommon.png", "frame_rare.png", "frame_veryrare.png",
                "frame_ultrarare.png", "frame_epic.png");
        for (String name : required) {
            for (boolean missing : List.of(true, false)) {
                IllegalStateException failure = assertThrows(IllegalStateException.class,
                        () -> new ShipArtwork(directory, data, List.of(), resource -> resource.equals(name)
                                ? missing ? null : new ByteArrayInputStream(new byte[]{1, 2, 3})
                                : resource(resource)));
                assertTrue(failure.getMessage().contains(name), failure.getMessage());
            }
        }
    }

    /** Reads bundled bytes through the same local resource boundary as production. */
    private static InputStream resource(String name) {
        return ShipArtworkTest.class.getResourceAsStream("/com/kor/admiralty/ui/resources/" + name);
    }

    /** Catches ImageIcon setters and exposed pixel buffers changing shared artwork. */
    @Test
    void imageHandlesRejectMutationAndDoNotExposeTheirPixels() throws Exception {
        Ship ship = ship("Class F Shuttle", ShipFaction.Federation, Role.Smc, Rarity.Common);
        GameData data = GameData.builder().ships(List.of(ship)).build();
        try (ShipArtwork artwork = ShipArtwork.open(directory, data, List.of(ship))) {
            ImageIcon icon = artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
            assertThrows(UnsupportedOperationException.class,
                    () -> icon.setImage(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)));
            assertThrows(UnsupportedOperationException.class, () -> icon.setDescription("changed"));
            assertThrows(UnsupportedOperationException.class, () -> icon.setImageObserver((img, flags, x, y, w, h) -> false));
            var copy = icon.getImage();
            var graphics = copy.getGraphics();
            try {
                graphics.setColor(java.awt.Color.MAGENTA);
                graphics.fillRect(0, 0, 64, 64);
            } finally {
                graphics.dispose();
            }
            copy.flush();
            assertPixels(baseline("bundled-shuttle.png"), icon);
            assertEquals(java.awt.MediaTracker.COMPLETE, icon.getImageLoadStatus());
        }
    }

    /** Catches treating bundled specific pixels as missing or changing source scaling. */
    @Test
    void bundledSpecificIsAvailableImmediatelyButGenericRemainsGeneric() throws Exception {
        Ship ship = ship("Class F Shuttle", ShipFaction.Federation, Role.Smc, Rarity.Common);
        GameData data = GameData.builder().ships(List.of(ship)).build();
        try (ShipArtwork artwork = ShipArtwork.open(directory, data, List.of())) {
            ImageIcon specific = artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
            assertPixels(baseline("bundled-shuttle.png"), specific);
            assertSame(specific, artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC));
            assertPixels(baseline("generic.png").getSubimage(9 * 64, 64, 64, 64),
                    artwork.forShip(ship, ShipArtwork.Presentation.GENERIC));
        }
    }

    /** Catches accepting equal-but-foreign Ships and invalid requests before any resource work. */
    @Test
    void rejectsNoncanonicalShipsAndNullArguments() {
        Ship ship = ship("Cruiser", ShipFaction.Federation, Role.Eng, Rarity.Epic);
        Ship foreign = ship("Cruiser", ShipFaction.Federation, Role.Eng, Rarity.Epic);
        GameData data = GameData.builder().ships(List.of(ship)).build();
        assertThrows(NullPointerException.class, () -> ShipArtwork.open(null, data, List.of()));
        assertThrows(NullPointerException.class, () -> ShipArtwork.open(directory, null, List.of()));
        assertThrows(NullPointerException.class, () -> ShipArtwork.open(directory, data, null));
        assertThrows(IllegalArgumentException.class, () -> ShipArtwork.open(directory, data, List.of(foreign)));
        try (ShipArtwork artwork = ShipArtwork.open(directory, data, List.of())) {
            assertThrows(NullPointerException.class, () -> artwork.forShip(null, ShipArtwork.Presentation.GENERIC));
            assertThrows(NullPointerException.class, () -> artwork.forShip(ship, null));
            assertThrows(IllegalArgumentException.class, () -> artwork.forShip(foreign, ShipArtwork.Presentation.GENERIC));
            ImageIcon valid = artwork.forShip(ship, ShipArtwork.Presentation.GENERIC);
            assertSame(valid, artwork.forShip(ship, ShipArtwork.Presentation.GENERIC));
            artwork.close();
            artwork.close();
            assertThrows(IllegalStateException.class, () -> artwork.forShip(ship, ShipArtwork.Presentation.GENERIC));
        }
    }

    /** Catches changed generic composition and unstable or missing fallback handles. */
    @Test
    void genericAndMissingSpecificRetainRecordedPixelsAndStableHandles() throws Exception {
        List<ShipFaction> factions = List.of(ShipFaction.None, ShipFaction.Federation,
                ShipFaction.Klingon, ShipFaction.Romulan, ShipFaction.JemHadar, ShipFaction.Universal);
        List<Role> roles = List.of(Role.None, Role.Eng, Role.Sci, Role.Tac, Role.Smc);
        List<Rarity> rarities = List.of(Rarity.None, Rarity.Common, Rarity.Uncommon,
                Rarity.Rare, Rarity.VeryRare, Rarity.UltraRare, Rarity.Epic);
        BufferedImage atlas = baseline("generic.png");
        for (ShipFaction faction : factions) {
            for (Role role : roles) {
                for (Rarity rarity : rarities) {
                    Ship ship = ship("__missing_artwork__", faction, role, rarity);
                    GameData data = GameData.builder().ships(List.of(ship)).build();
                    try (ShipArtwork artwork = ShipArtwork.open(directory, data, List.of(ship))) {
                        ImageIcon generic = artwork.forShip(ship, ShipArtwork.Presentation.GENERIC);
                        ImageIcon specific = artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
                        BufferedImage expected = atlas.getSubimage(
                                (factions.indexOf(faction) * 5 + roles.indexOf(role)) * 64,
                                rarities.indexOf(rarity) * 64, 64, 64);
                        assertPixels(expected, generic);
                        assertPixels(expected, specific);
                        assertSame(generic, artwork.forShip(ship, ShipArtwork.Presentation.GENERIC));
                        assertSame(specific, artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC));
                    }
                }
            }
        }
    }

    /** Builds a canonical reference value without using process-global application state. */
    private static Ship ship(String name, ShipFaction faction, Role role, Rarity rarity) {
        return new ShipImpl(faction, Tier.Tier1, rarity, role, name, 0, 0, 0,
                RuleType.All.rewardBonus(0), "");
    }

    /** Loads immutable pre-migration pixel evidence. */
    private static BufferedImage baseline(String name) throws Exception {
        return ImageIO.read(ShipArtworkTest.class.getResource("/ship-artwork/composition/" + name));
    }

    /** Compares actual paint output, including fixed dimensions and alpha. */
    private static void assertPixels(BufferedImage expected, ImageIcon icon) {
        assertEquals(64, icon.getIconWidth());
        assertEquals(64, icon.getIconHeight());
        BufferedImage painted = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        var graphics = painted.createGraphics();
        try {
            icon.paintIcon(null, graphics, 0, 0);
        } finally {
            graphics.dispose();
        }
        assertArrayEquals(expected.getRGB(0, 0, 64, 64, null, 0, 64),
                painted.getRGB(0, 0, 64, 64, null, 0, 64));
    }
}
