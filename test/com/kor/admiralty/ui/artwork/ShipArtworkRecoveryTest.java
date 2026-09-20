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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises corrupt-state recovery through opening, immediate lookup and restart. */
class ShipArtworkRecoveryTest {
    @TempDir
    Path directory;
    private final Ship ship = new ShipImpl(ShipFaction.Federation, Tier.Tier1, Rarity.Common,
            Role.None, "Recovery Cruiser", 0, 0, 0, RuleType.All.rewardBonus(0), "");
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    /** Quarantine preserves evidence before a successful acquisition rebuilds durable artwork. */
    @Test
    void corruptArchiveIsQuarantinedBeforeRebuildingAcrossRestart() throws Exception {
        byte[] corrupt = {1, 2, 3, 4};
        Files.write(directory.resolve("ship-artwork-v2.zip"), corrupt);
        byte[] legacy = {5, 6, 7};
        Files.write(directory.resolve("icons.zip"), legacy);
        AtomicReference<Consumer<BufferedImage>> completion = new AtomicReference<>();
        try (ShipArtwork artwork = open(completion::set, Files::move)) {
            ImageIcon specific = artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
            assertEquals(pixel(artwork.forShip(ship, ShipArtwork.Presentation.GENERIC)), pixel(specific));
            assertFalse(Files.exists(directory.resolve("ship-artwork-v2.zip")));
            assertRecoveryBytes(corrupt);
            completion.get().accept(source());
            SwingUtilities.invokeAndWait(() -> assertEquals(Color.MAGENTA.getRGB(), pixel(specific)));
        }
        assertRecoveryBytes(corrupt);
        assertArrayEquals(legacy, Files.readAllBytes(directory.resolve("icons.zip")));
        try (ShipArtwork reopened = open(callback -> fail("Rebuilt artwork must remain fresh"), Files::move)) {
            assertEquals(Color.MAGENTA.getRGB(), pixel(reopened.forShip(ship, ShipArtwork.Presentation.SPECIFIC)));
        }
    }

    /** A denied quarantine must protect the original even after successful in-memory acquisition. */
    @Test
    void failedQuarantinePreservesCorruptionAcrossCloseAndRestart() throws Exception {
        Path archive = directory.resolve("ship-artwork-v2.zip");
        byte[] corrupt = {1, 2, 3, 4};
        Files.write(archive, corrupt);
        byte[] legacy = {5, 6, 7};
        Files.write(directory.resolve("icons.zip"), legacy);
        IOException denied = new IOException("scripted quarantine denial");
        ShipArtworkArchive.FileMover mover = (source, target, options) -> {
            if (source.equals(archive)) throw denied;
            return Files.move(source, target, options);
        };
        List<java.util.logging.LogRecord> diagnostics = new ArrayList<>();
        var logger = java.util.logging.Logger.getLogger(ShipArtwork.class.getName());
        var handler = new java.util.logging.Handler() {
            /** Captures observable diagnostics for the failed filesystem operation. */
            @Override public void publish(java.util.logging.LogRecord record) { diagnostics.add(record); }
            @Override public void flush() { /* No buffered output is owned by this capture. */ }
            @Override public void close() { /* No resources are owned by this capture. */ }
        };
        logger.addHandler(handler);
        try {
            for (int launch = 0; launch < 2; launch++) {
                AtomicReference<Consumer<BufferedImage>> completion = new AtomicReference<>();
                try (ShipArtwork artwork = open(completion::set, mover)) {
                    ImageIcon specific = artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
                    assertEquals(pixel(artwork.forShip(ship, ShipArtwork.Presentation.GENERIC)), pixel(specific));
                    completion.get().accept(source());
                    SwingUtilities.invokeAndWait(() -> assertEquals(Color.MAGENTA.getRGB(), pixel(specific)));
                }
                assertArrayEquals(corrupt, Files.readAllBytes(archive));
                assertFalse(Files.exists(directory.resolve("ship-artwork-v2.zip.new")));
                assertArrayEquals(legacy, Files.readAllBytes(directory.resolve("icons.zip")));
            }
            assertEquals(2, diagnostics.stream().filter(record -> record.getThrown() == denied).count());
        } finally {
            logger.removeHandler(handler);
        }
        // A later launch can recover normally once the filesystem permits the move.
        try (ShipArtwork artwork = open(callback -> callback.accept(source()), Files::move)) {
            artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
        }
        assertRecoveryBytes(corrupt);
        try (ShipArtwork reopened = open(callback -> fail("Rebuilt artwork must remain fresh"), Files::move)) {
            assertEquals(Color.MAGENTA.getRGB(), pixel(reopened.forShip(ship, ShipArtwork.Presentation.SPECIFIC)));
        }
    }

    /** Invalid persisted relationships and content must be rejected as a whole before lookup. */
    @ParameterizedTest
    @ValueSource(strings = {"schema", "recipe", "metadata", "identity", "manifest-digest", "image-digest",
            "image", "truncated-image", "missing-entry", "extra-entry", "duplicate-entry"})
    void corruptStateIsRejectedAndPreserved(String corruption) throws Exception {
        try (ShipArtwork artwork = open(callback -> callback.accept(source()), Files::move)) {
            artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
        }
        byte[] corrupt = corruptArchive(corruption);
        try (ShipArtwork artwork = open(callback -> callback.accept(null), Files::move)) {
            assertEquals(pixel(artwork.forShip(ship, ShipArtwork.Presentation.GENERIC)),
                    pixel(artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC)));
            assertRecoveryBytes(corrupt);
        }
        try (ShipArtwork reopened = open(callback -> callback.accept(null), Files::move)) {
            assertEquals(pixel(reopened.forShip(ship, ShipArtwork.Presentation.GENERIC)),
                    pixel(reopened.forShip(ship, ShipArtwork.Presentation.SPECIFIC)));
        }
        assertRecoveryBytes(corrupt);
    }

    /** Damages one independent format contract, renewing digests except when testing a digest. */
    private byte[] corruptArchive(String corruption) throws Exception {
        Path archive = directory.resolve("ship-artwork-v2.zip");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            var names = zip.entries();
            while (names.hasMoreElements()) {
                ZipEntry entry = names.nextElement();
                try (var input = zip.getInputStream(entry)) {
                    entries.put(entry.getName(), input.readAllBytes());
                }
            }
        }
        String manifest = new String(entries.get("manifest.properties"), StandardCharsets.UTF_8);
        String imagePath = entries.keySet().stream().filter(name -> name.startsWith("artwork/")).findFirst().orElseThrow();
        switch (corruption) {
            case "schema" -> manifest = manifest.replace("schema.version=2", "schema.version=99");
            case "recipe" -> manifest = manifest.replace("recipe.version=1", "recipe.version=99");
            case "metadata" -> manifest = manifest.replace(now.toString(), "not-a-timestamp");
            case "identity" -> manifest = manifest.replace("entry.0.faction=Federation", "entry.0.faction=Klingon");
            case "manifest-digest" -> entries.put("manifest.sha256", new byte[] {0});
            case "image-digest" -> entries.put(imagePath, new byte[] {0});
            case "image", "truncated-image" -> {
                byte[] png = entries.get(imagePath);
                byte[] invalid = corruption.equals("image") ? new byte[] {0} : Arrays.copyOf(png, png.length - 12);
                entries.put(imagePath, invalid);
                manifest = manifest.replace(sha256(png), sha256(invalid));
            }
            case "missing-entry" -> entries.remove(imagePath);
            case "extra-entry" -> entries.put("unexpected.txt", new byte[] {0});
            case "duplicate-entry" -> entries.put("manifest.propertiez", entries.get("manifest.properties"));
            default -> throw new IllegalArgumentException(corruption);
        }
        entries.put("manifest.properties", manifest.getBytes(StandardCharsets.UTF_8));
        if (!corruption.equals("manifest-digest")) {
            entries.put("manifest.sha256", sha256(entries.get("manifest.properties")).getBytes(StandardCharsets.US_ASCII));
        }
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        if (corruption.equals("duplicate-entry")) {
            // Equal-length names preserve ZIP offsets while bypassing ZipOutputStream's duplicate guard.
            String bytes = new String(Files.readAllBytes(archive), StandardCharsets.ISO_8859_1);
            Files.write(archive, bytes.replace("manifest.propertiez", "manifest.properties")
                    .getBytes(StandardCharsets.ISO_8859_1));
        }
        return Files.readAllBytes(archive);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /** Opens production behavior with deterministic acquisition and a narrow filesystem fault seam. */
    private ShipArtwork open(Consumer<Consumer<BufferedImage>> acquisition, ShipArtworkArchive.FileMover mover) {
        return new ShipArtwork(directory, GameData.builder().ships(List.of(ship)).build(), List.of(),
                name -> getClass().getResourceAsStream("/com/kor/admiralty/ui/resources/" + name),
                (name, completed) -> acquisition.accept(completed), () -> now, mover);
    }

    /** Confirms the sole recovery file retains the exact unreadable archive. */
    private void assertRecoveryBytes(byte[] expected) throws Exception {
        try (var files = Files.list(directory)) {
            List<Path> recovery = files.filter(path -> path.getFileName().toString()
                    .startsWith("ship-artwork-v2.zip.corrupt-")).toList();
            assertEquals(1, recovery.size());
            assertArrayEquals(expected, Files.readAllBytes(recovery.getFirst()));
        }
    }

    /** Supplies recognizable pixels without transport or bundled specific artwork. */
    private static BufferedImage source() {
        BufferedImage result = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) result.setRGB(x, y, Color.MAGENTA.getRGB());
        }
        return result;
    }

    private static int pixel(ImageIcon icon) {
        return ((BufferedImage) icon.getImage()).getRGB(32, 32);
    }
}
