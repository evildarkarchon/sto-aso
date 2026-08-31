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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Specifies the trust boundary between the remote hash manifest and local
 * GameData files.
 */
class UpdateDataFilesTest {

    private static final String MD5_ABC = "900150983cd24fb0d6963f7d28e17f72";
    private static final String MD5_OLD = "149603e6c03516362a8da23f624db945";

    @TempDir
    Path tempDir;

    /**
     * Builds a complete, hand-checked manifest for the five required GameData
     * files.
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
     * Verifies a remote property name cannot become a download path outside the
     * fixed GameData set.
     *
     * @throws Exception if the local fixture cannot be written or the updater fails
     *                   unexpectedly
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
     * Verifies an absolute remote property name is rejected by the same
     * fixed-filename boundary.
     *
     * @throws Exception if the local fixture cannot be written or the updater fails
     *                   unexpectedly
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
     * Verifies an incomplete remote manifest cannot mix GameData versions or
     * replace the complete local manifest.
     *
     * @throws Exception if the local fixture cannot be written or the updater fails
     *                   unexpectedly
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
     * Verifies the exact legitimate five-file manifest remains a successful no-op
     * when every hash matches.
     *
     * @throws Exception if the local fixture cannot be written or the updater fails
     *                   unexpectedly
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
     * Verifies an I/O failure discards entries parsed before the remote response
     * became unreadable.
     */
    @Test
    void remoteResponseFailureDiscardsPartiallyParsedManifest() {
        UpdateDataFiles updater = new FailingReadUpdateDataFiles(tempDir);

        Properties remoteHashes = updater.loadRemoteHashes();

        assertTrue(remoteHashes.isEmpty());
    }

    /**
     * Verifies the remote manifest reader decodes non-ASCII property values as
     * UTF-8.
     *
     * @throws Exception if the UTF-8 fixture cannot be written or read
     */
    @Test
    void remoteManifestReaderUsesUtf8() throws Exception {
        Path manifest = tempDir.resolve("utf8.properties");
        Files.writeString(manifest, "description=caf\u00e9\n");
        UpdateDataFiles updater = new UpdateDataFiles(tempDir);
        Properties properties = new Properties();

        try (Reader reader = updater.openRemoteHashesReader(manifest.toUri().toURL())) {
            properties.load(reader);
        }

        assertEquals("caf\u00e9", properties.getProperty("description"));
    }

    /**
     * Verifies mismatched downloaded bytes are rejected before any live GameData
     * file or manifest is replaced.
     *
     * @throws Exception if the local fixture cannot be written or the updater fails
     *                   unexpectedly
     */
    @Test
    void digestMismatchRejectsStagedDownloadWithoutChangingLiveData() throws Exception {
        Properties localHashes = completeManifest(MD5_OLD);
        writeManifest(localHashes);
        for (String filename : UpdateDataFiles.FILENAMES) {
            Files.writeString(tempDir.resolve(filename), "old");
        }
        UpdateDataFiles updater = new DownloadingManifestUpdateDataFiles(
                tempDir,
                completeManifest(MD5_ABC),
                "wrong");

        UpdateDataFiles.Result result = updater.doInBackground();

        assertEquals(UpdateDataFiles.Result.FAILED, result);
        for (String filename : UpdateDataFiles.FILENAMES) {
            assertEquals("old", Files.readString(tempDir.resolve(filename)));
        }
        Properties persistedHashes = new Properties();
        try (Reader reader = Files.newBufferedReader(tempDir.resolve("hashes.md5"))) {
            persistedHashes.load(reader);
        }
        assertEquals(localHashes, persistedHashes);
    }

    /**
     * Verifies a failed live-file replacement restores every file and the prior
     * hash manifest.
     *
     * @throws Exception if the local fixture cannot be written or the updater fails
     *                   unexpectedly
     */
    @Test
    void installFailureRollsBackPreviouslyReplacedFiles() throws Exception {
        Properties localHashes = completeManifest(MD5_OLD);
        writeManifest(localHashes);
        for (String filename : UpdateDataFiles.FILENAMES) {
            Files.writeString(tempDir.resolve(filename), "old");
        }
        UpdateDataFiles updater = new FailingInstallUpdateDataFiles(
                tempDir,
                completeManifest(MD5_ABC),
                "abc");

        UpdateDataFiles.Result result = updater.doInBackground();

        assertEquals(UpdateDataFiles.Result.FAILED, result);
        for (String filename : UpdateDataFiles.FILENAMES) {
            assertEquals("old", Files.readString(tempDir.resolve(filename)));
        }
        Properties persistedHashes = new Properties();
        try (Reader reader = Files.newBufferedReader(tempDir.resolve("hashes.md5"))) {
            persistedHashes.load(reader);
        }
        assertEquals(localHashes, persistedHashes);
    }

    /**
     * Verifies a failed manifest commit restores all replaced data files and the
     * prior manifest.
     *
     * @throws Exception if the local fixture cannot be written or the updater fails
     *                   unexpectedly
     */
    @Test
    void manifestPublishFailureRollsBackInstalledFiles() throws Exception {
        Properties localHashes = completeManifest(MD5_OLD);
        writeManifest(localHashes);
        for (String filename : UpdateDataFiles.FILENAMES) {
            Files.writeString(tempDir.resolve(filename), "old");
        }
        UpdateDataFiles updater = new FailingManifestPublishUpdateDataFiles(
                tempDir,
                completeManifest(MD5_ABC),
                "abc");

        UpdateDataFiles.Result result = updater.doInBackground();

        assertEquals(UpdateDataFiles.Result.FAILED, result);
        for (String filename : UpdateDataFiles.FILENAMES) {
            assertEquals("old", Files.readString(tempDir.resolve(filename)));
        }
        Properties persistedHashes = new Properties();
        try (Reader reader = Files.newBufferedReader(tempDir.resolve("hashes.md5"))) {
            persistedHashes.load(reader);
        }
        assertEquals(localHashes, persistedHashes);
    }

    /**
     * Verifies an existing manifest is replaced non-atomically when the filesystem
     * rejects atomic replacement.
     *
     * @param failure filesystem-provider response to the atomic replacement attempt
     * @throws Exception if the local fixture cannot be written or the updater fails
     *                   unexpectedly
     */
    @ParameterizedTest
    @EnumSource(AtomicManifestFailure.class)
    void manifestPublishFallsBackWhenAtomicReplacementIsUnavailable(AtomicManifestFailure failure) throws Exception {
        writeManifest(completeManifest(MD5_OLD));
        for (String filename : UpdateDataFiles.FILENAMES) {
            Files.writeString(tempDir.resolve(filename), "old");
        }
        AtomicReplacementRejectingUpdateDataFiles updater = new AtomicReplacementRejectingUpdateDataFiles(
                tempDir,
                completeManifest(MD5_ABC),
                "abc",
                failure);

        UpdateDataFiles.Result result = updater.doInBackground();

        assertEquals(UpdateDataFiles.Result.DOWNLOADED, result);
        assertEquals(1, updater.atomicMoveAttempts);
        assertEquals(1, updater.fallbackMoveAttempts);
        for (String filename : UpdateDataFiles.FILENAMES) {
            assertEquals("abc", Files.readString(tempDir.resolve(filename)));
        }
        Properties persistedHashes = new Properties();
        try (Reader reader = Files.newBufferedReader(tempDir.resolve("hashes.md5"))) {
            persistedHashes.load(reader);
        }
        assertEquals(completeManifest(MD5_ABC), persistedHashes);
    }

    /**
     * Persists the local hash fixture through the same Java properties format used
     * in production.
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
     * Enumerates the filesystem failures for which atomic manifest replacement must
     * degrade gracefully.
     */
    private enum AtomicManifestFailure {
        ATOMIC_MOVE_NOT_SUPPORTED {
            @Override
            IOException create(Path source, Path target) {
                return new AtomicMoveNotSupportedException(
                        source.toString(),
                        target.toString(),
                        "simulated unsupported atomic replacement");
            }
        },
        TARGET_ALREADY_EXISTS {
            @Override
            IOException create(Path source, Path target) {
                return new FileAlreadyExistsException(target.toString());
            }
        };

        /**
         * Creates the provider-specific failure for one atomic replacement attempt.
         *
         * @param source staged manifest
         * @param target existing live manifest
         * @return exception raised by the simulated filesystem provider
         */
        abstract IOException create(Path source, Path target);
    }

    /**
     * Keeps local hashing and persistence real while replacing only the external
     * manifest download.
     */
    private static class ManifestUpdateDataFiles extends UpdateDataFiles {

        private final Properties remoteHashes;

        /**
         * Creates an updater backed by a deterministic remote manifest fixture.
         *
         * @param dataDirectory real temporary data directory
         * @param remoteHashes  manifest returned at the network boundary
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
     * Supplies deterministic downloaded bytes while keeping staging, hashing,
     * replacement, and persistence real.
     */
    private static class DownloadingManifestUpdateDataFiles extends ManifestUpdateDataFiles {

        private final String downloadedBytes;

        /**
         * Creates an updater backed by deterministic remote manifest and file
         * responses.
         *
         * @param dataDirectory   real temporary data directory
         * @param remoteHashes    manifest returned at the network boundary
         * @param downloadedBytes bytes written for every requested GameData file
         */
        private DownloadingManifestUpdateDataFiles(
                Path dataDirectory,
                Properties remoteHashes,
                String downloadedBytes) {
            super(dataDirectory, remoteHashes);
            this.downloadedBytes = downloadedBytes;
        }

        @Override
        protected boolean download(Path targetDirectory, String filename, String remoteUrl) {
            try {
                Files.writeString(targetDirectory.resolve(filename), downloadedBytes);
                return true;
            } catch (IOException cause) {
                throw new AssertionError("Unable to create downloaded-file fixture", cause);
            }
        }
    }

    /**
     * Simulates a filesystem failure after one staged file has replaced its live
     * counterpart.
     */
    private static final class FailingInstallUpdateDataFiles extends DownloadingManifestUpdateDataFiles {

        private int moveCount;

        /**
         * Creates an updater whose second live-file replacement fails.
         *
         * @param dataDirectory   real temporary data directory
         * @param remoteHashes    manifest returned at the network boundary
         * @param downloadedBytes bytes written for every requested GameData file
         */
        private FailingInstallUpdateDataFiles(
                Path dataDirectory,
                Properties remoteHashes,
                String downloadedBytes) {
            super(dataDirectory, remoteHashes, downloadedBytes);
        }

        @Override
        protected void moveStagedFile(Path source, Path target) throws IOException {
            moveCount++;
            if (moveCount == 2) {
                throw new IOException("simulated install failure");
            }
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Simulates failure at the atomic manifest publication commit point.
     */
    private static final class FailingManifestPublishUpdateDataFiles extends DownloadingManifestUpdateDataFiles {

        /**
         * Creates an updater whose manifest publication fails after all data files are
         * installed.
         *
         * @param dataDirectory   real temporary data directory
         * @param remoteHashes    manifest returned at the network boundary
         * @param downloadedBytes bytes written for every requested GameData file
         */
        private FailingManifestPublishUpdateDataFiles(
                Path dataDirectory,
                Properties remoteHashes,
                String downloadedBytes) {
            super(dataDirectory, remoteHashes, downloadedBytes);
        }

        @Override
        protected void publishHashManifest(Path source, Path target) throws IOException {
            throw new IOException("simulated manifest publication failure");
        }
    }

    /**
     * Rejects only atomic overwrite attempts while allowing the fallback
     * replacement to use the real filesystem.
     */
    private static final class AtomicReplacementRejectingUpdateDataFiles extends DownloadingManifestUpdateDataFiles {

        private final AtomicManifestFailure failure;
        private int atomicMoveAttempts;
        private int fallbackMoveAttempts;

        /**
         * Creates an updater whose filesystem boundary rejects atomic manifest
         * replacement.
         *
         * @param dataDirectory   real temporary data directory
         * @param remoteHashes    manifest returned at the network boundary
         * @param downloadedBytes bytes written for every requested GameData file
         * @param failure         exception raised for the atomic overwrite attempt
         */
        private AtomicReplacementRejectingUpdateDataFiles(
                Path dataDirectory,
                Properties remoteHashes,
                String downloadedBytes,
                AtomicManifestFailure failure) {
            super(dataDirectory, remoteHashes, downloadedBytes);
            this.failure = failure;
        }

        @Override
        protected void moveHashManifest(Path source, Path target, CopyOption... options) throws IOException {
            if (Files.exists(target) && Arrays.asList(options).contains(StandardCopyOption.ATOMIC_MOVE)) {
                atomicMoveAttempts++;
                throw failure.create(source, target);
            }
            if (Arrays.asList(options).equals(List.of(StandardCopyOption.REPLACE_EXISTING))) {
                fallbackMoveAttempts++;
            }
            Files.move(source, target, options);
        }
    }

    /**
     * Supplies a response that fails while closing after one syntactically complete
     * property was parsed.
     */
    private static final class FailingReadUpdateDataFiles extends UpdateDataFiles {

        /**
         * Creates an updater whose remote response fails after loading one complete
         * property.
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
     * Emits one complete manifest entry, then simulates a response failure while
     * the reader closes.
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
