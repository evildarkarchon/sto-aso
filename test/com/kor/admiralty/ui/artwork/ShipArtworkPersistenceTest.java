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

import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises durable Ship Artwork through creation, lookup and close. */
class ShipArtworkPersistenceTest {
    @TempDir
    Path directory;

    /** A successful source remains fresh and visible after reopening without touching legacy state. */
    @Test
    void successfulArtworkSurvivesReopeningWithoutOverwritingLegacyArchive() throws Exception {
        Path legacy = directory.resolve("icons.zip");
        byte[] legacyBytes = "legacy rollback evidence".getBytes(StandardCharsets.UTF_8);
        Files.write(legacy, legacyBytes);
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        Ship firstShip = ship("Persisted Cruiser");
        List<String> firstRequests = new ArrayList<>();

        try (ShipArtwork artwork = artwork(firstShip, firstRequests, now, Color.MAGENTA)) {
            ImageIcon icon = artwork.forShip(firstShip, ShipArtwork.Presentation.SPECIFIC);
            SwingUtilities.invokeAndWait(() -> assertEquals(Color.MAGENTA.getRGB(), pixel(icon)));
        }

        assertArrayEquals(legacyBytes, Files.readAllBytes(legacy));
        assertTrue(Files.exists(directory.resolve("ship-artwork-v2.zip")));

        Ship reopenedShip = ship("Persisted Cruiser");
        List<String> reopenedRequests = new ArrayList<>();
        try (ShipArtwork artwork = artwork(reopenedShip, reopenedRequests, now, Color.GREEN)) {
            ImageIcon icon = artwork.forShip(reopenedShip, ShipArtwork.Presentation.SPECIFIC);
            assertEquals(Color.MAGENTA.getRGB(), pixel(icon));
            assertEquals(List.of(), reopenedRequests, "Fresh persisted artwork must avoid reacquisition");

            now.updateAndGet(time -> time.plus(Duration.ofDays(7)));
            artwork.forShip(reopenedShip, ShipArtwork.Presentation.SPECIFIC);
            assertEquals(List.of(reopenedShip.getIconName()), reopenedRequests);
        }
    }

    /** The durable format exposes versioned production facts and digest relationships. */
    @Test
    void archiveRecordsVersionedIdentityFreshnessAndIntegrityEvidence() throws Exception {
        Instant succeededAt = Instant.parse("2026-02-03T04:05:06Z");
        AtomicReference<Instant> now = new AtomicReference<>(succeededAt);
        Ship ship = ship("Metadata Cruiser", ShipFaction.Klingon, Role.Tac, Rarity.Epic);
        try (ShipArtwork artwork = artwork(ship, new ArrayList<>(), now, Color.CYAN)) {
            artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
        }

        try (ZipFile archive = new ZipFile(directory.resolve("ship-artwork-v2.zip").toFile())) {
            byte[] manifestBytes = archive.getInputStream(archive.getEntry("manifest.properties")).readAllBytes();
            String manifestDigest = new String(
                    archive.getInputStream(archive.getEntry("manifest.sha256")).readAllBytes(),
                    StandardCharsets.US_ASCII).strip();
            assertEquals(sha256(manifestBytes), manifestDigest);

            Map<String, String> manifest = properties(manifestBytes);
            assertEquals("2", manifest.get("schema.version"));
            assertEquals("1", manifest.get("recipe.version"));
            assertEquals("1", manifest.get("source.count"));
            assertEquals(ship.getIconName(), decode(manifest.get("source.0.identity")));
            assertEquals(succeededAt.toString(), manifest.get("source.0.succeeded-at"));
            assertEquals("false", manifest.get("source.0.refresh-due"));
            assertEquals("1", manifest.get("entry.count"));
            assertEquals(ship.getIconName(), decode(manifest.get("entry.0.source-image")));
            assertEquals(ship.getFaction().name(), manifest.get("entry.0.faction"));
            assertEquals(ship.getRole().name(), manifest.get("entry.0.role"));
            assertEquals(ship.getRarity().name(), manifest.get("entry.0.rarity"));
            assertTrue(manifest.get("entry.0.path").contains(manifest.get("entry.0.identity")));
            byte[] png = archive.getInputStream(archive.getEntry(manifest.get("entry.0.path"))).readAllBytes();
            assertEquals(sha256(png), manifest.get("entry.0.sha256"));
        }
    }

    /** Freshness for one source cannot make artwork with different composition facts reusable. */
    @Test
    void changedCanonicalCompositionFactsDoNotReusePersistedPixels() throws Exception {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        Ship original = ship("Identity Cruiser", ShipFaction.Federation, Role.None, Rarity.Common);
        try (ShipArtwork artwork = artwork(original, new ArrayList<>(), now, Color.MAGENTA)) {
            artwork.forShip(original, ShipArtwork.Presentation.SPECIFIC);
        }

        Ship changed = ship("Identity Cruiser", ShipFaction.Federation, Role.Eng, Rarity.Epic);
        List<String> requests = new ArrayList<>();
        GameData changedData = GameData.builder().ships(List.of(changed)).build();
        try (ShipArtwork artwork = new ShipArtwork(directory, changedData, List.of(),
                name -> getClass().getResourceAsStream("/com/kor/admiralty/ui/resources/" + name),
                (name, completed) -> requests.add(name), now::get)) {
            ImageIcon icon = artwork.forShip(changed, ShipArtwork.Presentation.SPECIFIC);
            assertNotEquals(Color.MAGENTA.getRGB(), pixel(icon));
            assertEquals(List.of(changed.getIconName()), requests);
        }
    }

    /** A provider that rejects atomic replacement receives one completed-file fallback. */
    @Test
    void unsupportedAtomicReplacementFallsBackToCompletedArchive() throws Exception {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        Ship ship = ship("Fallback Cruiser");
        List<List<CopyOption>> attempts = new ArrayList<>();
        ShipArtworkArchive.FileMover mover = (source, target, options) -> {
            attempts.add(List.of(options));
            if (Arrays.asList(options).contains(StandardCopyOption.ATOMIC_MOVE)) {
                throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "scripted provider");
            }
            return Files.move(source, target, options);
        };
        GameData data = GameData.builder().ships(List.of(ship)).build();
        try (ShipArtwork artwork = new ShipArtwork(directory, data, List.of(),
                name -> getClass().getResourceAsStream("/com/kor/admiralty/ui/resources/" + name),
                (name, completed) -> completed.accept(source(Color.MAGENTA)), now::get, mover)) {
            artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
        }

        assertEquals(2, attempts.size());
        assertTrue(attempts.getFirst().contains(StandardCopyOption.ATOMIC_MOVE));
        assertEquals(List.of(StandardCopyOption.REPLACE_EXISTING), attempts.getLast());
        assertPersistedPixel(ship, now, Color.MAGENTA.getRGB());
    }

    /** A real replacement-write failure leaves the previously installed archive reusable. */
    @Test
    void replacementWriteFailurePreservesPreviousArchive() throws Exception {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        Ship ship = ship("Write Failure Cruiser");
        persist(ship, now, Color.MAGENTA);
        byte[] previous = Files.readAllBytes(directory.resolve("ship-artwork-v2.zip"));
        Files.createDirectory(directory.resolve("ship-artwork-v2.zip.new"));

        refreshWith(ship, now, Color.GREEN, Files::move);

        assertArrayEquals(previous, Files.readAllBytes(directory.resolve("ship-artwork-v2.zip")));
        assertPersistedPixel(ship, now, Color.MAGENTA.getRGB());
    }

    /** A failed completed-file installation leaves the previously installed archive reusable. */
    @Test
    void installationFailurePreservesPreviousArchive() throws Exception {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        Ship ship = ship("Install Failure Cruiser");
        persist(ship, now, Color.MAGENTA);
        byte[] previous = Files.readAllBytes(directory.resolve("ship-artwork-v2.zip"));

        refreshWith(ship, now, Color.GREEN, (source, target, options) -> {
            if (Arrays.asList(options).contains(StandardCopyOption.ATOMIC_MOVE)) {
                throw new AtomicMoveNotSupportedException(
                        source.toString(), target.toString(), "scripted provider");
            }
            // Exercise rollback after a non-atomic provider has already damaged the destination.
            Files.delete(target);
            throw new IOException("scripted installation failure after destination removal");
        });

        assertArrayEquals(previous, Files.readAllBytes(directory.resolve("ship-artwork-v2.zip")));
        assertPersistedPixel(ship, now, Color.MAGENTA.getRGB());
    }

    /** Opens one lifetime with deterministic acquisition and time. */
    private ShipArtwork artwork(Ship ship, List<String> requests,
                                AtomicReference<Instant> now, Color acquiredColor) {
        GameData data = GameData.builder().ships(List.of(ship)).build();
        return new ShipArtwork(directory, data, List.of(),
                name -> getClass().getResourceAsStream("/com/kor/admiralty/ui/resources/" + name),
                (name, completed) -> {
                    requests.add(name);
                    completed.accept(source(acquiredColor));
                }, now::get);
    }

    /** Persists one successful source through the public lifetime boundary. */
    private void persist(Ship ship, AtomicReference<Instant> now, Color color) {
        try (ShipArtwork artwork = artwork(ship, new ArrayList<>(), now, color)) {
            artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
        }
    }

    /** Refreshes loaded artwork and closes through a selected installation boundary. */
    private void refreshWith(Ship ship, AtomicReference<Instant> now, Color color,
                             ShipArtworkArchive.FileMover mover) {
        GameData data = GameData.builder().ships(List.of(ship)).build();
        try (ShipArtwork artwork = new ShipArtwork(directory, data, List.of(),
                name -> getClass().getResourceAsStream("/com/kor/admiralty/ui/resources/" + name),
                (name, completed) -> completed.accept(source(color)), now::get, mover)) {
            artwork.refreshOnline(List.of(ship));
        }
    }

    /** Reopens through the Ship Artwork seam and verifies no fresh acquisition occurs. */
    private void assertPersistedPixel(Ship ship, AtomicReference<Instant> now, int expected) {
        List<String> requests = new ArrayList<>();
        try (ShipArtwork artwork = artwork(ship, requests, now, Color.ORANGE)) {
            assertEquals(expected, pixel(artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC)));
            assertEquals(List.of(), requests);
        }
    }

    /** Supplies one canonical Ship without packaged specific artwork. */
    private static Ship ship(String name) {
        return ship(name, ShipFaction.Federation, Role.None, Rarity.Common);
    }

    /** Supplies canonical composition facts explicitly. */
    private static Ship ship(String name, ShipFaction faction, Role role, Rarity rarity) {
        return new ShipImpl(faction, Tier.Tier1, rarity, role,
                name, 0, 0, 0, RuleType.All.rewardBonus(0), "");
    }

    /** Supplies recognizable source pixels independently of composition. */
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

    /** Reads a center pixel outside every composition frame. */
    private static int pixel(ImageIcon icon) {
        return ((BufferedImage) icon.getImage()).getRGB(32, 32);
    }

    /** Parses the archive's deliberately simple line-oriented manifest. */
    private static Map<String, String> properties(byte[] bytes) {
        Map<String, String> result = new HashMap<>();
        new String(bytes, StandardCharsets.UTF_8).lines().forEach(line -> {
            int separator = line.indexOf('=');
            result.put(line.substring(0, separator), line.substring(separator + 1));
        });
        return result;
    }

    private static String decode(String encoded) {
        return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
