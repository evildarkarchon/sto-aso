/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.artwork;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.io.GameData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the headless operator boundary with real archives and explicit directories. */
class ShipArtworkToolTest {
    private static final List<String> GAME_DATA_FILES = List.of(
            "ships.csv", "renamed.csv", "traits.csv", "events.csv", "assignments.csv");

    @TempDir
    Path directory;

    /** Inspect reports both archive types and leaves their bytes and directory entries untouched. */
    @Test
    void inspectInventoriesLegacyAndV2WithoutWriting() throws Exception {
        copyGameData();
        Path legacy = copyLegacy("recognizable-and-unmatched.zip");
        Path versioned = writeValidVersionedArchive();
        byte[] legacyBytes = Files.readAllBytes(legacy);
        byte[] versionedBytes = Files.readAllBytes(versioned);
        FileTime legacyModified = Files.getLastModifiedTime(legacy);
        FileTime versionedModified = Files.getLastModifiedTime(versioned);
        Set<String> before = filenames();

        Invocation human = invoke("inspect", "--data-directory", directory.toString());
        Invocation json = invoke("inspect", "--data-directory", directory.toString(), "--json");

        assertEquals(3, human.exitCode());
        assertEquals(3, json.exitCode());
        assertTrue(human.output().contains("Retired_Prototype.png"));
        assertTrue(human.output().contains("eligible=0, migrated=0"));
        assertTrue(json.output().contains("Retired_Prototype.png"));
        assertTrue(json.output().contains("\"eligible\":0,\"migrated\":0"));
        assertTrue(json.output().contains("\"reason\":\"ALREADY_CURRENT\""));
        assertTrue(json.output().stripLeading().startsWith("{"));
        assertArrayEquals(legacyBytes, Files.readAllBytes(legacy));
        assertArrayEquals(versionedBytes, Files.readAllBytes(versioned));
        assertEquals(legacyModified, Files.getLastModifiedTime(legacy));
        assertEquals(versionedModified, Files.getLastModifiedTime(versioned));
        assertEquals(before, filenames());
    }

    /** Legacy-only inspection reports a candidate without claiming to have migrated it. */
    @Test
    void inspectReportsEligibleArtworkWithoutWriting() throws Exception {
        copyGameData();
        copyLegacy("recognizable-and-unmatched.zip");

        Invocation result = invoke("inspect", "--data-directory", directory.toString(), "--json");

        assertEquals(3, result.exitCode());
        assertTrue(result.output().contains("\"eligible\":1,\"migrated\":0"));
        assertTrue(result.output().contains("\"reason\":\"WOULD_MIGRATE\""));
        assertFalse(Files.exists(directory.resolve(ShipArtworkArchive.FILENAME)));
    }

    /** Offline migration preserves rollback bytes and writes only recognizable stale pixels to v2. */
    @Test
    void migratePreservesLegacyAndReportsUnmatchedEntry() throws Exception {
        copyGameData();
        Path legacy = copyLegacy("recognizable-and-unmatched.zip");
        byte[] original = Files.readAllBytes(legacy);

        Invocation result = invoke("migrate", "--data-directory", directory.toString());

        assertEquals(3, result.exitCode());
        assertTrue(result.output().contains("Retired_Prototype.png"));
        assertTrue(result.output().contains("eligible=1, migrated=1"));
        assertArrayEquals(original, Files.readAllBytes(legacy));
        Path versioned = directory.resolve(ShipArtworkArchive.FILENAME);
        assertTrue(Files.exists(versioned));
        ShipArtworkArchive.State state = new ShipArtworkArchive(directory, Files::move).load();
        assertEquals(1, state.legacy().size());
        assertTrue(state.artwork().isEmpty());
    }

    /** Verify reads every valid v2 relationship without changing the archive or creating files. */
    @Test
    void verifyValidVersionedArchiveWithoutWriting() throws Exception {
        copyGameData();
        Path versioned = writeValidVersionedArchive();
        byte[] original = Files.readAllBytes(versioned);
        FileTime modified = Files.getLastModifiedTime(versioned);
        Set<String> before = filenames();

        Invocation result = invoke("verify", "--data-directory", directory.toString());

        assertEquals(0, result.exitCode());
        assertArrayEquals(original, Files.readAllBytes(versioned));
        assertEquals(modified, Files.getLastModifiedTime(versioned));
        assertEquals(before, filenames());
    }

    /** Invalid v2 bytes are reported as findings while the evidence stays in place. */
    @Test
    void malformedVersionedArchiveIsFindingWithoutQuarantine() throws Exception {
        copyGameData();
        Path versioned = directory.resolve(ShipArtworkArchive.FILENAME);
        byte[] malformed = {1, 2, 3, 4};
        Files.write(versioned, malformed);
        Set<String> before = filenames();

        Invocation result = invoke("verify", "--data-directory", directory.toString());

        assertEquals(3, result.exitCode());
        assertFalse(result.output().isBlank());
        assertArrayEquals(malformed, Files.readAllBytes(versioned));
        assertEquals(before, filenames());
    }

    /** A filesystem read failure is operational, distinct from malformed archive contents. */
    @Test
    void unreadableVersionedArchiveIsOperationalFailure() throws Exception {
        Path inaccessible = Files.createDirectory(directory.resolve(ShipArtworkArchive.FILENAME));

        Invocation result = invoke("verify", "--data-directory", directory.toString(), "--json");

        assertEquals(4, result.exitCode());
        assertTrue(result.output().contains("\"status\":\"operational-failure\""));
        assertTrue(Files.isDirectory(inaccessible));
    }

    /** A source with no current artwork entry fails verification despite a valid manifest digest. */
    @Test
    void orphanSourceMetadataIsFinding() throws Exception {
        copyGameData();
        Path versioned = writeValidVersionedArchive();
        rewriteManifestWithOrphanSource(versioned);
        byte[] invalid = Files.readAllBytes(versioned);

        Invocation result = invoke("verify", "--data-directory", directory.toString());

        assertEquals(3, result.exitCode());
        assertArrayEquals(invalid, Files.readAllBytes(versioned));
    }

    /** The migration transport guard throws an uncaught Error if future code requests remote pixels. */
    @Test
    void offlineAcquisitionGuardFailsLoudly() throws Exception {
        copyGameData();
        GameData gameData = GameData.load(directory);
        Ship ship = gameData.ships().stream()
                .filter(candidate -> candidate.getName().equals("Class F Shuttle"))
                .findFirst().orElseThrow();
        try (ShipArtwork artwork = new ShipArtwork(directory, gameData, List.of(),
                name -> name.equals(ship.getIconName()) ? null : packagedResource(name),
                ShipArtworkTool::refuseAcquisition)) {
            AssertionError failure = assertThrows(AssertionError.class,
                    () -> artwork.refreshOnline(List.of(ship)));
            assertTrue(failure.getMessage().contains("Offline migration attempted network acquisition"));
        }
    }

    /** Missing or malformed command targets are invalid arguments, never inferred directories. */
    @Test
    void argumentsRequireAnExplicitExistingDataDirectory() {
        assertEquals(2, invoke("inspect").exitCode());
        assertEquals(2, invoke("verify", "--data-directory", directory.resolve("absent").toString())
                .exitCode());
        assertEquals(2, invoke("migrate", "--data-directory", directory.toString(), "--unknown")
                .exitCode());
        assertEquals(2, invoke("unknown", "--data-directory", directory.toString()).exitCode());
    }

    /** A selected directory that cannot supply required local GameData is an operational failure. */
    @Test
    void missingGameDataIsOperationalFailure() {
        Invocation result = invoke("migrate", "--data-directory", directory.toString());

        assertEquals(4, result.exitCode());
        assertFalse(result.error().isBlank() && result.output().isBlank());
        assertFalse(Files.exists(directory.resolve(ShipArtworkArchive.FILENAME)));
    }

    /** A failed v2 installation is observable while the rollback archive remains untouched. */
    @Test
    void migrationWriteFailureIsOperationalFailure() throws Exception {
        copyGameData();
        Path legacy = copyLegacy("recognizable.zip");
        byte[] original = Files.readAllBytes(legacy);
        Files.createDirectory(directory.resolve(ShipArtworkArchive.REPLACEMENT_FILENAME));

        Invocation result = invoke("migrate", "--data-directory", directory.toString(), "--json");

        assertEquals(4, result.exitCode());
        assertTrue(result.output().contains("\"status\":\"operational-failure\""));
        assertArrayEquals(original, Files.readAllBytes(legacy));
        assertFalse(Files.exists(directory.resolve(ShipArtworkArchive.FILENAME)));
    }

    /** Runs the command's testable entry point while collecting both output streams. */
    private static Invocation invoke(String... arguments) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(output, true, java.nio.charset.StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(error, true, java.nio.charset.StandardCharsets.UTF_8)) {
            int exitCode = ShipArtworkTool.run(arguments, out, err);
            return new Invocation(exitCode, output.toString(java.nio.charset.StandardCharsets.UTF_8),
                    error.toString(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /** Copies a small, complete reference-data fixture into this test's explicit target directory. */
    private void copyGameData() throws IOException {
        for (String filename : GAME_DATA_FILES) {
            copyResource("/gamedata/" + filename, directory.resolve(filename));
        }
    }

    /** Copies one frozen pre-migration archive without changing its contents. */
    private Path copyLegacy(String fixture) throws IOException {
        Path target = directory.resolve("icons.zip");
        copyResource("/ship-artwork/legacy/" + fixture, target);
        return target;
    }

    /** Copies a required classpath fixture to a temporary filesystem path. */
    private void copyResource(String resource, Path target) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing fixture: " + resource);
            Files.copy(input, target);
        }
    }

    /** Creates a valid current entry through the same archive writer used by the application. */
    private Path writeValidVersionedArchive() throws Exception {
        Ship ship = GameData.load(directory).ships().stream()
                .filter(candidate -> candidate.getName().equals("Class F Shuttle"))
                .findFirst().orElseThrow();
        ShipArtworkArchive.Identity identity = ShipArtworkArchive.Identity.from(ship);
        BufferedImage pixels = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        pixels.setRGB(0, 0, 0xff4488cc);
        new ShipArtworkArchive(directory, Files::move).save(new ShipArtworkArchive.State(
                Map.of(identity, pixels), Map.of(),
                Map.of(identity.sourceImage(), Instant.parse("2026-01-01T00:00:00Z")), Set.of()));
        return directory.resolve(ShipArtworkArchive.FILENAME);
    }

    /** Adds a well-formed but unreferenced source while keeping the manifest digest valid. */
    private void rewriteManifestWithOrphanSource(Path archive) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            var existing = zip.entries();
            while (existing.hasMoreElements()) {
                ZipEntry entry = existing.nextElement();
                try (InputStream input = zip.getInputStream(entry)) {
                    entries.put(entry.getName(), input.readAllBytes());
                }
            }
        }
        String manifest = new String(entries.get("manifest.properties"), StandardCharsets.UTF_8);
        String orphan = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("orphan.png".getBytes(StandardCharsets.UTF_8));
        String changed = manifest.replace("source.count=1\n", "source.count=2\n")
                + "source.1.identity=" + orphan + "\n"
                + "source.1.succeeded-at=2026-01-01T00:00:00Z\n"
                + "source.1.refresh-due=false\n";
        byte[] changedBytes = changed.getBytes(StandardCharsets.UTF_8);
        entries.put("manifest.properties", changedBytes);
        entries.put("manifest.sha256", (java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(changedBytes)) + "\n")
                .getBytes(StandardCharsets.US_ASCII));
        Path replacement = directory.resolve("orphan-source.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(replacement))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        Files.move(replacement, archive, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Opens the same packaged composition assets as the production artwork lifetime. */
    private static InputStream packagedResource(String name) {
        return ShipArtworkToolTest.class.getResourceAsStream("/com/kor/admiralty/ui/resources/" + name);
    }

    /** Lists direct children so a read-only command cannot leave replacement or recovery files. */
    private Set<String> filenames() throws IOException {
        try (Stream<Path> children = Files.list(directory)) {
            return children.map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet());
        }
    }

    /** Captured CLI result, including the exit category and both output streams. */
    private record Invocation(int exitCode, String output, String error) { }
}
