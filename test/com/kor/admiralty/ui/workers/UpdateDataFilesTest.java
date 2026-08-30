/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty.ui.workers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Specifies the trust boundary between the remote hash manifest and local GameData files.
 */
class UpdateDataFilesTest {

    @TempDir
    Path tempDir;

    /**
     * Verifies a remote property name cannot become a download path outside the fixed GameData set.
     *
     * @throws Exception if the local fixture cannot be written or the updater fails unexpectedly
     */
    @Test
    void unexpectedRemoteFilenameRejectsEntireManifest() throws Exception {
        Properties localHashes = completeManifest("current");
        writeManifest(localHashes);
        Properties remoteHashes = completeManifest("current");
        remoteHashes.setProperty("../../outside.txt", "");
        UpdateDataFiles updater = new ManifestUpdateDataFiles(tempDir, remoteHashes);

        UpdateDataFiles.Result result = updater.doInBackground();

        String persistedManifest = Files.readString(tempDir.resolve("hashes.md5"));
        assertEquals(UpdateDataFiles.Result.FAILED, result);
        assertFalse(persistedManifest.contains("outside.txt"));
        assertFalse(Files.exists(tempDir.getParent().resolve("outside.txt")));
    }

    /**
     * Verifies an absolute remote property name is rejected by the same fixed-filename boundary.
     *
     * @throws Exception if the local fixture cannot be written or the updater fails unexpectedly
     */
    @Test
    void absoluteRemoteFilenameRejectsEntireManifest() throws Exception {
        Properties localHashes = completeManifest("current");
        writeManifest(localHashes);
        Path outsideFile = tempDir.getParent().resolve("absolute-outside.txt").toAbsolutePath();
        Properties remoteHashes = completeManifest("current");
        remoteHashes.setProperty(outsideFile.toString(), "");
        UpdateDataFiles updater = new ManifestUpdateDataFiles(tempDir, remoteHashes);

        UpdateDataFiles.Result result = updater.doInBackground();

        assertEquals(UpdateDataFiles.Result.FAILED, result);
        assertFalse(Files.exists(outsideFile));
    }

    /**
     * Verifies an incomplete remote manifest cannot mix GameData versions or replace the complete local manifest.
     *
     * @throws Exception if the local fixture cannot be written or the updater fails unexpectedly
     */
    @Test
    void incompleteRemoteManifestIsRejected() throws Exception {
        Properties localHashes = completeManifest("current");
        writeManifest(localHashes);
        Properties remoteHashes = new Properties();
        remoteHashes.setProperty("ships.csv", "current");
        UpdateDataFiles updater = new ManifestUpdateDataFiles(tempDir, remoteHashes);

        UpdateDataFiles.Result result = updater.doInBackground();

        String persistedManifest = Files.readString(tempDir.resolve("hashes.md5"));
        assertEquals(UpdateDataFiles.Result.FAILED, result);
        assertTrue(persistedManifest.contains("traits.csv"));
    }

    /**
     * Verifies the exact legitimate five-file manifest remains a successful no-op when every hash matches.
     *
     * @throws Exception if the local fixture cannot be written or the updater fails unexpectedly
     */
    @Test
    void completeUnchangedRemoteManifestRemainsCurrent() throws Exception {
        Properties localHashes = completeManifest("current");
        writeManifest(localHashes);
        UpdateDataFiles updater = new ManifestUpdateDataFiles(tempDir, completeManifest("current"));

        UpdateDataFiles.Result result = updater.doInBackground();

        assertEquals(UpdateDataFiles.Result.CURRENT, result);
        Properties persistedHashes = new Properties();
        try (Reader reader = Files.newBufferedReader(tempDir.resolve("hashes.md5"))) {
            persistedHashes.load(reader);
        }
        assertEquals(completeManifest("current"), persistedHashes);
    }

    /**
     * Verifies an I/O failure discards entries parsed before the remote response became unreadable.
     */
    @Test
    void remoteResponseFailureDiscardsPartiallyParsedManifest() {
        UpdateDataFiles updater = new FailingReadUpdateDataFiles(tempDir);

        Properties remoteHashes = updater.loadRemoteHashes();

        assertTrue(remoteHashes.isEmpty());
    }

    /**
     * Builds a complete, hand-checked manifest for the five required GameData files.
     *
     * @param hash common fixture hash
     * @return complete manifest with no unexpected filenames
     */
    private static Properties completeManifest(String hash) {
        Properties properties = new Properties();
        properties.setProperty("ships.csv", hash);
        properties.setProperty("renamed.csv", hash);
        properties.setProperty("events.csv", hash);
        properties.setProperty("assignments.csv", hash);
        properties.setProperty("traits.csv", hash);
        return properties;
    }

    /**
     * Persists the local hash fixture through the same Java properties format used in production.
     *
     * @param properties manifest to persist
     * @throws IOException if the fixture cannot be written
     */
    private void writeManifest(Properties properties) throws IOException {
        try (Writer writer = Files.newBufferedWriter(tempDir.resolve("hashes.md5"))) {
            properties.store(writer, "");
        }
    }

    /**
     * Keeps local hashing and persistence real while replacing only the external manifest download.
     */
    private static final class ManifestUpdateDataFiles extends UpdateDataFiles {

        private final Properties remoteHashes;

        /**
         * Creates an updater backed by a deterministic remote manifest fixture.
         *
         * @param dataDirectory real temporary data directory
         * @param remoteHashes manifest returned at the network boundary
         */
        private ManifestUpdateDataFiles(Path dataDirectory, Properties remoteHashes) {
            super(dataDirectory);
            this.remoteHashes = remoteHashes;
        }

        @Override
        protected Properties loadRemoteHashes() {
            return remoteHashes;
        }
    }

    /**
     * Supplies a response that fails while closing after one syntactically complete property was parsed.
     */
    private static final class FailingReadUpdateDataFiles extends UpdateDataFiles {

        /**
         * Creates an updater whose remote response fails after loading one complete property.
         *
         * @param dataDirectory real temporary data directory
         */
        private FailingReadUpdateDataFiles(Path dataDirectory) {
            super(dataDirectory);
        }

        @Override
        protected Reader openRemoteHashesReader(URL url) {
            return new FailingAfterEntryReader();
        }
    }

    /**
     * Emits one complete manifest entry, then simulates a response failure while the reader closes.
     */
    private static final class FailingAfterEntryReader extends Reader {

        private static final String PREFIX = "ships.csv=current\n";
        private int index;

        @Override
        public int read(char[] buffer, int offset, int length) throws IOException {
            if (index >= PREFIX.length()) {
                return -1;
            }
            int count = Math.min(length, PREFIX.length() - index);
            PREFIX.getChars(index, index + count, buffer, offset);
            index += count;
            return count;
        }

        @Override
        public void close() throws IOException {
            throw new IOException("simulated interrupted response");
        }
    }
}
