/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import com.kor.admiralty.Globals;

/**
 * Determines freshness and synchronously refreshes the GameData files beneath
 * one already-resolved application data directory.
 */
public final class GameDataRefresh {

    private static final long REFRESH_INTERVAL_MILLIS = Duration.ofDays(7).toMillis();
    private static final List<String> GAME_DATA_FILENAMES = List.of(
            Globals.FILENAME_SHIPCACHE,
            Globals.FILENAME_RENAMED,
            Globals.FILENAME_EVENTS,
            Globals.FILENAME_ASSIGNMENTS,
            Globals.FILENAME_TRAITS);
    private static final Set<String> GAME_DATA_FILENAME_SET = Set.copyOf(GAME_DATA_FILENAMES);
    private static final Pattern MD5_DIGEST = Pattern.compile("[0-9a-fA-F]{32}");
    private static final char[] LOWER_HEX_DIGITS = "0123456789abcdef".toCharArray();

    private final Path dataDirectory;
    private final Path manifest;
    private final GameDataRefreshSource source;

    /**
     * Creates a GameData Refresh rooted in an already-resolved data directory.
     *
     * @param dataDirectory directory containing the live GameData set
     */
    public GameDataRefresh(Path dataDirectory) {
        this(dataDirectory, new GitHubGameDataRefreshSource());
    }

    /**
     * Creates a refresh with an internal content source for deterministic package
     * tests and production adapters.
     *
     * @param dataDirectory directory containing the live GameData set
     * @param source        remote content source that cannot select local paths
     */
    GameDataRefresh(Path dataDirectory, GameDataRefreshSource source) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.source = Objects.requireNonNull(source, "source");
        manifest = dataDirectory.resolve(Globals.FILENAME_HASHES);
    }

    /**
     * Reports whether the live digest manifest is missing or at least seven days
     * old.
     *
     * @return {@code true} when a refresh should be attempted
     * @throws IOException retained for the complete timestamp-based freshness contract
     */
    public boolean isDue() throws IOException {
        return Files.notExists(manifest)
                || Files.getLastModifiedTime(manifest).toMillis()
                        <= System.currentTimeMillis() - REFRESH_INTERVAL_MILLIS;
    }

    /**
     * Performs one synchronous refresh attempt and returns its immutable outcome.
     * Expected remote, verification, and manifest-publication failures are
     * categorized; unexpected programming failures propagate.
     *
     * @return current, refreshed, or failed outcome for this attempt
     */
    public GameDataRefreshOutcome refresh() {
        Properties remoteManifest;
        try {
            remoteManifest = readRemoteManifest();
        } catch (InvalidManifestException cause) {
            return GameDataRefreshOutcome.failed(
                    GameDataRefreshOutcome.FailureCategory.VERIFICATION,
                    cause,
                    null);
        } catch (IOException cause) {
            return GameDataRefreshOutcome.failed(
                    GameDataRefreshOutcome.FailureCategory.REMOTE_ACQUISITION,
                    cause,
                    null);
        }

        try {
            validateRemoteManifest(remoteManifest);
        } catch (IllegalArgumentException cause) {
            return GameDataRefreshOutcome.failed(
                    GameDataRefreshOutcome.FailureCategory.VERIFICATION,
                    cause,
                    null);
        }

        Properties localManifest;
        try {
            localManifest = readOrCalculateLocalManifest();
        } catch (IOException | InvalidManifestException | NoSuchAlgorithmException cause) {
            return GameDataRefreshOutcome.failed(
                    GameDataRefreshOutcome.FailureCategory.VERIFICATION,
                    cause,
                    null);
        }

        if (!remoteManifest.equals(localManifest)) {
            // Issue #33 adds verified changed-file installation. Until then a changed
            // manifest must never be reported as current or refreshed.
            throw new IllegalStateException("Changed GameData installation is not available yet.");
        }

        try {
            renewManifest(remoteManifest);
        } catch (IOException cause) {
            return GameDataRefreshOutcome.failed(
                    GameDataRefreshOutcome.FailureCategory.INSTALLATION,
                    cause,
                    null);
        }
        return GameDataRefreshOutcome.current();
    }

    /**
     * Reads the complete remote manifest as UTF-8. A read or close failure prevents
     * partially parsed properties from escaping this method.
     *
     * @return fully acquired remote manifest
     * @throws IOException              if opening, reading, or closing the response fails
     * @throws InvalidManifestException if the response is not valid Java properties syntax
     */
    private Properties readRemoteManifest() throws IOException, InvalidManifestException {
        Properties properties = new Properties();
        try (InputStream input = source.openManifest()) {
            Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
            try {
                properties.load(reader);
            } catch (IllegalArgumentException cause) {
                throw new InvalidManifestException("Malformed remote GameData manifest.", cause);
            }
        }
        return properties;
    }

    /**
     * Requires an exact complete filename set whose entries cannot identify paths
     * and whose values are compatibility MD5 digests.
     *
     * @param remoteManifest untrusted remote properties
     * @throws IllegalArgumentException if any filename is unsafe, unexpected, or missing
     */
    private static void validateRemoteManifest(Properties remoteManifest) {
        Set<String> filenames = remoteManifest.stringPropertyNames();
        try {
            for (String filename : filenames) {
                Path entry = Path.of(filename);
                if (entry.isAbsolute() || entry.getNameCount() != 1) {
                    throw new IllegalArgumentException("Unsafe GameData manifest filename: " + filename);
                }
            }
        } catch (InvalidPathException cause) {
            throw new IllegalArgumentException("Invalid GameData manifest filename.", cause);
        }
        if (!filenames.equals(GAME_DATA_FILENAME_SET)) {
            throw new IllegalArgumentException(
                    "GameData manifest must contain exactly " + GAME_DATA_FILENAME_SET + "; received " + filenames);
        }
        for (String filename : GAME_DATA_FILENAMES) {
            String digest = remoteManifest.getProperty(filename);
            if (!MD5_DIGEST.matcher(digest).matches()) {
                throw new IllegalArgumentException("Invalid MD5 digest for GameData file: " + filename);
            }
        }
    }

    /**
     * Loads the live manifest when present or calculates in-memory hashes without
     * creating live metadata.
     *
     * @return local digests used only for comparison
     * @throws IOException if an existing manifest or GameData file cannot be read
     * @throws InvalidManifestException if the live manifest has invalid properties syntax
     * @throws NoSuchAlgorithmException if the compatibility MD5 digest is unavailable
     */
    private Properties readOrCalculateLocalManifest()
            throws IOException, InvalidManifestException, NoSuchAlgorithmException {
        if (Files.exists(manifest)) {
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
                try {
                    properties.load(reader);
                } catch (IllegalArgumentException cause) {
                    throw new InvalidManifestException("Malformed live GameData manifest.", cause);
                }
            }
            return properties;
        }

        Properties properties = new Properties();
        for (String filename : GAME_DATA_FILENAMES) {
            properties.setProperty(filename, calculateHash(dataDirectory.resolve(filename)));
        }
        return properties;
    }

    /**
     * Calculates the lower-case MD5 digest required by the shipped manifest format.
     *
     * @param file GameData file to hash
     * @return lower-case hexadecimal digest
     * @throws IOException if the file cannot be read
     * @throws NoSuchAlgorithmException if MD5 is unavailable
     */
    private static String calculateHash(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] bytes = digest.digest(Files.readAllBytes(file));
        StringBuilder encoded = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            int unsigned = value & 0xff;
            encoded.append(LOWER_HEX_DIGITS[unsigned >>> 4]);
            encoded.append(LOWER_HEX_DIGITS[unsigned & 0x0f]);
        }
        return encoded.toString();
    }

    /**
     * Renews existing validated metadata without rewriting it, or safely publishes
     * a new manifest after local-file comparison succeeds.
     *
     * @param remoteManifest validated manifest to publish when none exists
     * @throws IOException if the timestamp or new-manifest publication fails
     */
    private void renewManifest(Properties remoteManifest) throws IOException {
        if (Files.exists(manifest)) {
            Files.setLastModifiedTime(manifest, FileTime.from(Instant.now()));
            return;
        }

        Path stagedManifest = Files.createTempFile(dataDirectory, ".gamedata-manifest-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(
                    stagedManifest,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                remoteManifest.store(writer, "");
            }
            Files.move(stagedManifest, manifest);
        } finally {
            Files.deleteIfExists(stagedManifest);
        }
    }

    /**
     * Distinguishes untrusted manifest syntax errors from programming failures.
     */
    private static final class InvalidManifestException extends Exception {

        /**
         * Creates a verification error retaining the parser's diagnostic cause.
         *
         * @param message stable diagnostic context
         * @param cause   properties parser failure
         */
        private InvalidManifestException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
