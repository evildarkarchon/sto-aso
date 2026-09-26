/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.artwork;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.RuleType;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;
import com.kor.admiralty.io.GameData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Exercises legacy migration through the application-owned Ship Artwork lifetime. */
class ShipArtworkMigrationTest {
    @TempDir
    Path directory;

    /**
     * Catches migration that loses recognizable pixels, rewrites rollback bytes, or starts a
     * remote request merely because legacy state exists.
     */
    @Test
    void recognizableLegacyArtworkIsImmediateStaleFallbackWithoutStartupAcquisition() throws Exception {
        Path legacy = copyFixture("recognizable.zip");
        byte[] original = Files.readAllBytes(legacy);
        Ship shuttle = ship("Class F Shuttle", ShipFaction.Federation, Role.Smc, Rarity.Common);
        GameData gameData = GameData.builder().ships(List.of(shuttle)).build();
        List<String> requests = new ArrayList<>();

        try (ShipArtwork artwork = new ShipArtwork(directory, gameData, List.of(),
                name -> name.equals(shuttle.getIconName()) ? null : resource(name),
                (name, completed) -> {
                    requests.add(name);
                    completed.accept(null);
                })) {
            assertEquals(List.of(), requests);
            assertArchivedPixels(legacy, shuttle.getIconName(),
                    artwork.forShip(shuttle, ShipArtwork.Presentation.SPECIFIC));
            assertEquals(List.of(shuttle.getIconName()), requests);
        }

        assertArrayEquals(original, Files.readAllBytes(legacy));
    }

    /**
     * Catches treating legacy pixels as a current v2 identity or failing to persist stale fallback
     * separately from the untouched source archive.
     */
    @Test
    void migratedFallbackSurvivesRestartWithoutSuppressingAcquisition() throws Exception {
        Path legacy = copyFixture("recognizable.zip");
        Ship shuttle = ship("Class F Shuttle", ShipFaction.Federation, Role.Smc, Rarity.Common);
        int[] expected = archivedPixels(legacy, shuttle.getIconName());
        GameData gameData = GameData.builder().ships(List.of(shuttle)).build();

        try (ShipArtwork ignored = new ShipArtwork(directory, gameData, List.of(),
                name -> name.equals(shuttle.getIconName()) ? null : resource(name))) {
            // Migration is a startup concern even when no Ship card is requested.
        }
        assertTrue(Files.exists(directory.resolve("ship-artwork-v2.zip")));
        Files.delete(legacy);

        List<String> requests = new ArrayList<>();
        try (ShipArtwork artwork = new ShipArtwork(directory, gameData, List.of(),
                name -> name.equals(shuttle.getIconName()) ? null : resource(name),
                (name, completed) -> {
                    requests.add(name);
                    completed.accept(null);
                })) {
            assertArrayEquals(expected, pixels(artwork.forShip(shuttle, ShipArtwork.Presentation.SPECIFIC)));
            assertEquals(List.of(shuttle.getIconName()), requests,
                    "Migrated pixels must remain due for a real current acquisition");
        }
    }

    /** Catches silently dropping an unreadable entry or discarding an earlier valid one. */
    @Test
    void migrationReportsUnreadableEntriesAndSalvagesEarlierPixels() throws Exception {
        Path legacy = copyFixture("recognizable-and-unreadable.zip");
        byte[] original = Files.readAllBytes(legacy);
        Ship shuttle = ship("Class F Shuttle", ShipFaction.Federation, Role.Smc, Rarity.Common);
        GameData gameData = GameData.builder().ships(List.of(shuttle)).build();

        try (ShipArtwork artwork = new ShipArtwork(directory, gameData, List.of(),
                name -> name.equals(shuttle.getIconName()) ? null : resource(name))) {
            LegacyArtworkMigration.Outcome outcome = artwork.migrationOutcome();
            assertTrue(outcome.archivePresent());
            assertNull(outcome.archiveError());
            assertEquals(1, outcome.matched());
            assertEquals(1, outcome.migrated());
            assertEquals(1, outcome.unreadable());
            assertEquals(0, outcome.unmatched());
            assertEquals(2, outcome.details().size());
            assertEquals("Class_F_Shuttle.png", outcome.details().get(0).filename());
            assertEquals("Class F Shuttle", outcome.details().get(0).shipName());
            assertEquals(LegacyArtworkMigration.Disposition.MATCHED, outcome.details().get(0).disposition());
            assertEquals(LegacyArtworkMigration.Reason.MIGRATED, outcome.details().get(0).reason());
            assertEquals("Broken_Artwork.png", outcome.details().get(1).filename());
            assertEquals(LegacyArtworkMigration.Disposition.UNREADABLE, outcome.details().get(1).disposition());
            assertEquals(LegacyArtworkMigration.Reason.UNREADABLE_IMAGE, outcome.details().get(1).reason());
            assertFalse(outcome.details().get(1).explanation().isBlank());
            assertArchivedPixels(legacy, shuttle.getIconName(),
                    artwork.forShip(shuttle, ShipArtwork.Presentation.SPECIFIC));
        }
        assertArrayEquals(original, Files.readAllBytes(legacy));
    }

    /** Catches importing a readable filename that no supplied canonical Ship owns. */
    @Test
    void readableUnknownShipIsReportedAndOmittedFromV2() throws Exception {
        Path legacy = copyFixture("recognizable-and-unmatched.zip");
        byte[] original = Files.readAllBytes(legacy);
        Ship shuttle = ship("Class F Shuttle", ShipFaction.Federation, Role.Smc, Rarity.Common);
        GameData gameData = GameData.builder().ships(List.of(shuttle)).build();

        try (ShipArtwork artwork = new ShipArtwork(directory, gameData, List.of(), resource ->
                resource.equals(shuttle.getIconName()) ? null : resource(resource))) {
            LegacyArtworkMigration.Outcome outcome = artwork.migrationOutcome();
            assertEquals(1, outcome.matched());
            assertEquals(1, outcome.migrated());
            assertEquals(0, outcome.unreadable());
            assertEquals(1, outcome.unmatched());
            assertEquals("Retired_Prototype.png", outcome.details().get(1).filename());
            assertEquals(LegacyArtworkMigration.Reason.NO_CANONICAL_SHIP, outcome.details().get(1).reason());
            assertFalse(outcome.details().get(1).explanation().isBlank());
        }

        assertEquals(1, new ShipArtworkArchive(directory, Files::move).load().legacy().size());
        assertArrayEquals(original, Files.readAllBytes(legacy));
    }

    /** Catches carrying an old stale association forward after its Ship leaves GameData. */
    @Test
    void removedCanonicalShipIsDroppedFromPreviouslyMigratedV2State() throws Exception {
        Path legacy = copyFixture("recognizable.zip");
        byte[] original = Files.readAllBytes(legacy);
        Ship shuttle = ship("Class F Shuttle", ShipFaction.Federation, Role.Smc, Rarity.Common);
        try (ShipArtwork ignored = new ShipArtwork(directory,
                GameData.builder().ships(List.of(shuttle)).build(), List.of(),
                name -> name.equals(shuttle.getIconName()) ? null : resource(name))) {
            // Closing persists only the stale pixels recognized by this GameData.
        }
        assertEquals(1, new ShipArtworkArchive(directory, Files::move).load().legacy().size());

        Ship remaining = ship("Other Ship", ShipFaction.Federation, Role.Eng, Rarity.Common);
        try (ShipArtwork artwork = new ShipArtwork(directory,
                GameData.builder().ships(List.of(remaining)).build(), List.of(),
                ShipArtworkMigrationTest::resource)) {
            assertEquals(0, artwork.migrationOutcome().matched());
            assertEquals(2, artwork.migrationOutcome().unmatched());
        }

        assertTrue(new ShipArtworkArchive(directory, Files::move).load().legacy().isEmpty());
        assertArrayEquals(original, Files.readAllBytes(legacy));
    }

    /** Catches losing the canonical association when a recognizable image cannot be decoded. */
    @Test
    void mappedUnreadableEntryReportsItsShipWithoutMigration() throws Exception {
        Path legacy = writeLegacyEntry("Class_F_Shuttle.png", "not a PNG".getBytes(StandardCharsets.UTF_8));
        byte[] original = Files.readAllBytes(legacy);
        Ship shuttle = ship("Class F Shuttle", ShipFaction.Federation, Role.Smc, Rarity.Common);

        try (ShipArtwork artwork = new ShipArtwork(directory,
                GameData.builder().ships(List.of(shuttle)).build(), List.of(),
                name -> name.equals(shuttle.getIconName()) ? null : resource(name))) {
            LegacyArtworkMigration.Outcome outcome = artwork.migrationOutcome();
            assertEquals(0, outcome.matched());
            assertEquals(0, outcome.migrated());
            assertEquals(1, outcome.unreadable());
            assertEquals(0, outcome.unmatched());
            assertEquals("Class F Shuttle", outcome.details().getFirst().shipName());
            assertEquals(LegacyArtworkMigration.Reason.UNREADABLE_IMAGE,
                    outcome.details().getFirst().reason());
        }

        assertArrayEquals(original, Files.readAllBytes(legacy));
        assertFalse(Files.exists(directory.resolve("ship-artwork-v2.zip")));
    }

    /** Catches choosing either Ship when distinct canonical names share one legacy filename. */
    @Test
    void ambiguousFilenameIsReportedAndOmitted() throws Exception {
        Ship first = ship("A B", ShipFaction.Federation, Role.Eng, Rarity.Common);
        Ship second = ship("A-B", ShipFaction.Klingon, Role.Tac, Rarity.Common);
        assertEquals("A_B.png", first.getIconName());
        assertEquals(first.getIconName(), second.getIconName());
        Path legacy = writeLegacyEntry("A_B.png", validPng());
        byte[] original = Files.readAllBytes(legacy);

        try (ShipArtwork artwork = new ShipArtwork(directory,
                GameData.builder().ships(List.of(first, second)).build(), List.of(),
                ShipArtworkMigrationTest::resource)) {
            LegacyArtworkMigration.Outcome outcome = artwork.migrationOutcome();
            assertEquals(0, outcome.matched());
            assertEquals(0, outcome.migrated());
            assertEquals(0, outcome.unreadable());
            assertEquals(1, outcome.unmatched());
            assertEquals(LegacyArtworkMigration.Reason.AMBIGUOUS_FILENAME,
                    outcome.details().getFirst().reason());
            assertFalse(outcome.details().getFirst().explanation().isBlank());
        }

        assertFalse(Files.exists(directory.resolve("ship-artwork-v2.zip")));
        assertArrayEquals(original, Files.readAllBytes(legacy));
    }

    /** Catches destructive recovery when the legacy ZIP itself cannot be opened. */
    @Test
    void malformedLegacyArchiveIsReportedAndRetained() throws Exception {
        Path legacy = directory.resolve("icons.zip");
        byte[] original = "not a ZIP".getBytes(StandardCharsets.UTF_8);
        Files.write(legacy, original);
        Ship shuttle = ship("Class F Shuttle", ShipFaction.Federation, Role.Smc, Rarity.Common);

        try (ShipArtwork artwork = new ShipArtwork(directory,
                GameData.builder().ships(List.of(shuttle)).build(), List.of(),
                name -> name.equals(shuttle.getIconName()) ? null : resource(name))) {
            LegacyArtworkMigration.Outcome outcome = artwork.migrationOutcome();
            assertTrue(outcome.archivePresent());
            assertFalse(outcome.archiveError().isBlank());
            assertEquals(0, outcome.migrated());
            assertTrue(outcome.details().isEmpty());
        }

        assertArrayEquals(original, Files.readAllBytes(legacy));
        assertFalse(Files.exists(directory.resolve("ship-artwork-v2.zip")));
    }

    /** Catches a failed v2 installation changing the rollback archive. */
    @Test
    void failedMigrationWriteLeavesOriginalArchiveByteForByteUntouched() throws Exception {
        Path legacy = copyFixture("recognizable.zip");
        byte[] original = Files.readAllBytes(legacy);
        Ship shuttle = ship("Class F Shuttle", ShipFaction.Federation, Role.Smc, Rarity.Common);

        try (ShipArtwork artwork = new ShipArtwork(directory,
                GameData.builder().ships(List.of(shuttle)).build(), List.of(),
                name -> name.equals(shuttle.getIconName()) ? null : resource(name),
                (name, completed) -> completed.accept(null), Instant::now,
                (source, target, options) -> { throw new IOException("scripted v2 installation failure"); })) {
            assertEquals(1, artwork.migrationOutcome().migrated());
            assertArchivedPixels(legacy, shuttle.getIconName(),
                    artwork.forShip(shuttle, ShipArtwork.Presentation.SPECIFIC));
        }

        assertArrayEquals(original, Files.readAllBytes(legacy));
        assertFalse(Files.exists(directory.resolve("ship-artwork-v2.zip")));
    }

    /** Catches stale legacy pixels overriding a successful current v2 composition. */
    @Test
    void currentVersionedArtworkWinsAndLegacyIsOnlyReportedAsMatched() throws Exception {
        Ship shuttle = ship("Class F Shuttle", ShipFaction.Federation, Role.Smc, Rarity.Common);
        GameData gameData = GameData.builder().ships(List.of(shuttle)).build();
        try (ShipArtwork artwork = new ShipArtwork(directory, gameData, List.of(),
                name -> name.equals(shuttle.getIconName()) ? null : resource(name),
                (name, completed) -> completed.accept(solidSource(Color.MAGENTA)))) {
            artwork.forShip(shuttle, ShipArtwork.Presentation.SPECIFIC);
        }
        Path legacy = copyFixture("recognizable.zip");
        byte[] original = Files.readAllBytes(legacy);
        List<String> requests = new ArrayList<>();

        try (ShipArtwork artwork = new ShipArtwork(directory, gameData, List.of(),
                name -> name.equals(shuttle.getIconName()) ? null : resource(name),
                (name, completed) -> {
                    requests.add(name);
                    completed.accept(null);
                })) {
            LegacyArtworkMigration.Outcome outcome = artwork.migrationOutcome();
            assertEquals(1, outcome.matched());
            assertEquals(0, outcome.migrated());
            assertEquals(LegacyArtworkMigration.Reason.ALREADY_CURRENT,
                    outcome.details().getFirst().reason());
            assertEquals(Color.MAGENTA.getRGB(),
                    ((BufferedImage) artwork.forShip(shuttle, ShipArtwork.Presentation.SPECIFIC)
                            .getImage()).getRGB(32, 32));
            assertEquals(List.of(), requests);
        }
        assertArrayEquals(original, Files.readAllBytes(legacy));
    }

    /** Copies frozen legacy bytes to the isolated application data directory. */
    private Path copyFixture(String name) throws IOException {
        Path legacy = directory.resolve("icons.zip");
        try (InputStream input = getClass().getResourceAsStream("/ship-artwork/legacy/" + name)) {
            if (input == null) throw new IOException("Missing legacy fixture: " + name);
            Files.copy(input, legacy);
        }
        return legacy;
    }

    /** Writes one controlled legacy ZIP entry for cases absent from the frozen fixtures. */
    private Path writeLegacyEntry(String filename, byte[] bytes) throws IOException {
        Path legacy = directory.resolve("icons.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(legacy))) {
            zip.putNextEntry(new ZipEntry(filename));
            zip.write(bytes);
            zip.closeEntry();
        }
        return legacy;
    }

    /** Encodes a small real PNG for filename-mapping tests. */
    private static byte[] validPng() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB), "png", output)) {
            throw new IOException("PNG writer unavailable");
        }
        return output.toByteArray();
    }

    /** Supplies a visible source center without depending on a bundled Ship image. */
    private static BufferedImage solidSource(Color color) {
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

    /** Supplies one canonical Ship with the same source filename as the frozen archive. */
    private static Ship ship(String name, ShipFaction faction, Role role, Rarity rarity) {
        return new ShipImpl(faction, Tier.Tier1, rarity, role,
                name, 0, 0, 0, RuleType.All.rewardBonus(0), "");
    }

    /** Loads packaged composition assets while allowing a test to hide optional source artwork. */
    private static InputStream resource(String name) {
        return ShipArtworkMigrationTest.class.getResourceAsStream("/com/kor/admiralty/ui/resources/" + name);
    }

    /** Compares all composed ARGB pixels against frozen legacy output. */
    private static void assertArchivedPixels(Path archive, String key, ImageIcon actual) throws IOException {
        assertArrayEquals(archivedPixels(archive, key), pixels(actual));
    }

    /** Reads independently frozen pixels before a test removes its temporary legacy copy. */
    private static int[] archivedPixels(Path archive, String key) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile());
             InputStream input = zip.getInputStream(zip.getEntry(key))) {
            BufferedImage expected = ImageIO.read(input);
            return expected.getRGB(0, 0, 64, 64, null, 0, 64);
        }
    }

    /** Reads the defensive image snapshot returned by the stable artwork handle. */
    private static int[] pixels(ImageIcon artwork) {
        return ((BufferedImage) artwork.getImage()).getRGB(0, 0, 64, 64, null, 0, 64);
    }
}
