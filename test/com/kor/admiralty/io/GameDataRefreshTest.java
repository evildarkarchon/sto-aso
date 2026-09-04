/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty.io;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Specifies the synchronous GameData Refresh contract through its external
 * interface and real temporary directories.
 */
class GameDataRefreshTest {

    private static final String MD5_ABC = "900150983cd24fb0d6963f7d28e17f72";
    private static final List<String> GAME_DATA_FILENAMES = List.of(
            "ships.csv",
            "renamed.csv",
            "events.csv",
            "assignments.csv",
            "traits.csv");

    @TempDir
    Path tempDir;

    /**
     * Verifies a current outcome cannot claim changed files or failure evidence.
     */
    @Test
    void currentOutcomeContainsOnlyCurrentStatus() {
        GameDataRefreshOutcome outcome = GameDataRefreshOutcome.current();

        assertEquals(GameDataRefreshOutcome.Status.CURRENT, outcome.status());
        assertTrue(outcome.changedFiles().isEmpty());
        assertTrue(outcome.failureCategory().isEmpty());
        assertTrue(outcome.diagnosticCause().isEmpty());
        assertTrue(outcome.recoveryDirectory().isEmpty());
    }

    /**
     * Verifies a refreshed outcome requires and defensively retains a non-empty
     * immutable changed-file set.
     */
    @Test
    void refreshedOutcomeRequiresNonEmptyImmutableChanges() {
        Set<String> suppliedChanges = new HashSet<String>(Set.of("ships.csv"));

        GameDataRefreshOutcome outcome = GameDataRefreshOutcome.refreshed(suppliedChanges);
        suppliedChanges.add("events.csv");

        assertEquals(GameDataRefreshOutcome.Status.REFRESHED, outcome.status());
        assertEquals(Set.of("ships.csv"), outcome.changedFiles());
        assertThrows(UnsupportedOperationException.class, () -> outcome.changedFiles().add("traits.csv"));
        assertTrue(outcome.failureCategory().isEmpty());
        assertTrue(outcome.diagnosticCause().isEmpty());
        assertTrue(outcome.recoveryDirectory().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> GameDataRefreshOutcome.refreshed(Set.of()));
    }

    /**
     * Verifies failed outcomes claim no changes while retaining their stable
     * category and all available diagnostic evidence.
     */
    @Test
    void failedOutcomeRetainsCategoryAndEvidenceWithoutClaimingChanges() {
        IOException cause = new IOException("simulated installation failure");
        Path recoveryDirectory = tempDir.resolve("recovery");

        GameDataRefreshOutcome outcome = GameDataRefreshOutcome.failed(
                GameDataRefreshOutcome.FailureCategory.INSTALLATION,
                cause,
                recoveryDirectory);

        assertEquals(GameDataRefreshOutcome.Status.FAILED, outcome.status());
        assertTrue(outcome.changedFiles().isEmpty());
        assertEquals(GameDataRefreshOutcome.FailureCategory.INSTALLATION, outcome.failureCategory().orElseThrow());
        assertEquals(cause, outcome.diagnosticCause().orElseThrow());
        assertEquals(recoveryDirectory, outcome.recoveryDirectory().orElseThrow());
        assertEquals(
                Set.of(
                        GameDataRefreshOutcome.FailureCategory.REMOTE_ACQUISITION,
                        GameDataRefreshOutcome.FailureCategory.VERIFICATION,
                        GameDataRefreshOutcome.FailureCategory.INSTALLATION,
                        GameDataRefreshOutcome.FailureCategory.RECOVERY),
                Set.of(GameDataRefreshOutcome.FailureCategory.values()));
        assertThrows(NullPointerException.class, () -> GameDataRefreshOutcome.failed(null, cause, null));
    }

    /**
     * Verifies a complete matching remote manifest renews only the manifest
     * timestamp and does not request or replace GameData files.
     *
     * @throws Exception if a temporary fixture cannot be written or refreshed
     */
    @Test
    void completeUnchangedManifestReportsCurrentWithoutReplacingGameData() throws Exception {
        String manifestContents = completeManifest(MD5_ABC);
        Path manifest = tempDir.resolve("hashes.md5");
        Files.writeString(manifest, manifestContents);
        FileTime staleTime = FileTime.from(Instant.now().minus(Duration.ofDays(8)));
        Files.setLastModifiedTime(manifest, staleTime);
        Map<String, FileTime> dataTimestamps = new LinkedHashMap<String, FileTime>();
        for (String filename : GAME_DATA_FILENAMES) {
            Path dataFile = tempDir.resolve(filename);
            Files.writeString(dataFile, "live-" + filename);
            FileTime timestamp = Files.getLastModifiedTime(dataFile);
            dataTimestamps.put(filename, timestamp);
        }
        ScriptedSource source = new ScriptedSource(manifestContents);
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source);

        GameDataRefreshOutcome outcome = refresh.refresh();

        assertEquals(GameDataRefreshOutcome.Status.CURRENT, outcome.status());
        assertEquals(1, source.manifestRequests);
        assertTrue(source.gameDataRequests.isEmpty());
        assertTrue(Files.getLastModifiedTime(manifest).compareTo(staleTime) > 0);
        for (String filename : GAME_DATA_FILENAMES) {
            assertEquals("live-" + filename, Files.readString(tempDir.resolve(filename)));
            assertEquals(dataTimestamps.get(filename), Files.getLastModifiedTime(tempDir.resolve(filename)));
        }
    }

    /**
     * Verifies in-memory local hashes can establish a current set without
     * publishing live metadata until the validated comparison succeeds.
     *
     * @throws Exception if a temporary GameData fixture cannot be written or refreshed
     */
    @Test
    void missingManifestWithMatchingFilesPublishesValidatedCurrentManifest() throws Exception {
        for (String filename : GAME_DATA_FILENAMES) {
            Files.writeString(tempDir.resolve(filename), "abc");
        }
        ScriptedSource source = new ScriptedSource(completeManifest(MD5_ABC));
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source);

        GameDataRefreshOutcome outcome = refresh.refresh();

        assertEquals(GameDataRefreshOutcome.Status.CURRENT, outcome.status());
        assertTrue(source.gameDataRequests.isEmpty());
        Properties persistedManifest = new Properties();
        try (Reader reader = Files.newBufferedReader(tempDir.resolve("hashes.md5"), StandardCharsets.UTF_8)) {
            persistedManifest.load(reader);
        }
        assertEquals(Set.copyOf(GAME_DATA_FILENAMES), persistedManifest.stringPropertyNames());
        for (String filename : GAME_DATA_FILENAMES) {
            assertEquals(MD5_ABC, persistedManifest.getProperty(filename));
        }
    }

    /**
     * Verifies the refresh module owns one manifest stream lifetime without
     * closing the same remote response twice.
     *
     * @throws Exception if the local manifest fixture cannot be written or refreshed
     */
    @Test
    void remoteManifestStreamIsClosedExactlyOnce() throws Exception {
        String manifestContents = completeManifest(MD5_ABC);
        Files.writeString(tempDir.resolve("hashes.md5"), manifestContents);
        ScriptedSource source = new ScriptedSource(manifestContents);
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source);

        GameDataRefreshOutcome outcome = refresh.refresh();

        assertEquals(GameDataRefreshOutcome.Status.CURRENT, outcome.status());
        assertEquals(1, source.manifestCloseCount);
    }

    /**
     * Verifies a response that fails after yielding a complete property is
     * discarded as remote acquisition failure and cannot suppress retry.
     *
     * @throws Exception if the failed attempt unexpectedly publishes live metadata
     */
    @Test
    void partialRemoteReadFailsAcquisitionAndLeavesMissingManifestDue() throws Exception {
        IOException readFailure = new IOException("simulated partial response");
        ScriptedSource source = new ScriptedSource("ships.csv=" + MD5_ABC + "\n", readFailure);
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source);

        GameDataRefreshOutcome outcome = refresh.refresh();

        assertEquals(GameDataRefreshOutcome.Status.FAILED, outcome.status());
        assertTrue(outcome.changedFiles().isEmpty());
        assertEquals(
                GameDataRefreshOutcome.FailureCategory.REMOTE_ACQUISITION,
                outcome.failureCategory().orElseThrow());
        assertSame(readFailure, outcome.diagnosticCause().orElseThrow());
        assertFalse(Files.exists(tempDir.resolve("hashes.md5")));
        assertTrue(refresh.isDue());
        assertTrue(source.gameDataRequests.isEmpty());
    }

    /**
     * Verifies a traversal entry cannot become a local destination outside the
     * fixed GameData set.
     *
     * @throws Exception if the manifest fixtures cannot be written or inspected
     */
    @Test
    void unexpectedRemoteFilenameIsRejectedBeforeAnyLocalWrite() throws Exception {
        Path outsideFile = tempDir.getParent().resolve("outside.txt");

        assertRejectedManifest(completeManifestWithExtra("../outside.txt"));

        assertFalse(Files.exists(outsideFile));
    }

    /**
     * Verifies an absolute manifest entry cannot select a local destination.
     *
     * @throws Exception if the manifest fixtures cannot be written or inspected
     */
    @Test
    void absoluteRemoteFilenameIsRejectedBeforeAnyLocalWrite() throws Exception {
        Path outsideFile = tempDir.getParent().resolve("absolute-outside.txt").toAbsolutePath();

        assertRejectedManifest(completeManifestWithExtra(outsideFile.toString()));

        assertFalse(Files.exists(outsideFile));
    }

    /**
     * Verifies a manifest missing any fixed GameData filename cannot replace live
     * metadata.
     *
     * @throws Exception if the manifest fixtures cannot be written or inspected
     */
    @Test
    void incompleteRemoteManifestIsRejectedBeforeAnyLocalWrite() throws Exception {
        assertRejectedManifest("ships.csv=" + MD5_ABC + "\n");
    }

    /**
     * Verifies each validated remote entry carries the compatibility MD5 digest
     * shape before comparison can reach any installation path.
     *
     * @throws Exception if the manifest fixtures cannot be written or inspected
     */
    @Test
    void nonMd5RemoteDigestIsRejectedBeforeAnyLocalWrite() throws Exception {
        assertRejectedManifest(completeManifest("not-an-md5"));
    }

    /**
     * Verifies remote bytes are decoded as UTF-8 before validation and diagnostic
     * evidence is retained.
     *
     * @throws Exception if the manifest fixtures cannot be written or inspected
     */
    @Test
    void remoteManifestIsDecodedAsUtf8() throws Exception {
        GameDataRefreshOutcome outcome = assertRejectedManifest(completeManifestWithExtra("café.csv"));

        assertTrue(outcome.diagnosticCause().orElseThrow().getMessage().contains("café.csv"));
    }

    /**
     * Verifies malformed properties syntax from the remote boundary is reported
     * as verification failure rather than hidden as a programming defect.
     *
     * @throws Exception if the live manifest fixture cannot be written
     */
    @Test
    void malformedRemoteManifestIsVerificationFailure() throws Exception {
        Files.writeString(tempDir.resolve("hashes.md5"), completeManifest(MD5_ABC));
        ScriptedSource source = new ScriptedSource("ships.csv=\\uZZZZ\n");
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source);

        GameDataRefreshOutcome outcome = refresh.refresh();

        assertEquals(GameDataRefreshOutcome.Status.FAILED, outcome.status());
        assertEquals(
                GameDataRefreshOutcome.FailureCategory.VERIFICATION,
                outcome.failureCategory().orElseThrow());
        assertTrue(outcome.diagnosticCause().isPresent());
        assertTrue(source.gameDataRequests.isEmpty());
    }

    /**
     * Verifies malformed live metadata is an operational verification failure,
     * while unrelated source programming errors continue to propagate.
     *
     * @throws Exception if the live manifest fixture cannot be written
     */
    @Test
    void malformedLocalManifestIsVerificationFailure() throws Exception {
        String malformedManifest = "ships.csv=\\uZZZZ\n";
        Path manifest = tempDir.resolve("hashes.md5");
        Files.writeString(manifest, malformedManifest);
        ScriptedSource source = new ScriptedSource(completeManifest(MD5_ABC));
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source);

        GameDataRefreshOutcome outcome = refresh.refresh();

        assertEquals(GameDataRefreshOutcome.Status.FAILED, outcome.status());
        assertEquals(
                GameDataRefreshOutcome.FailureCategory.VERIFICATION,
                outcome.failureCategory().orElseThrow());
        assertTrue(outcome.diagnosticCause().isPresent());
        assertEquals(malformedManifest, Files.readString(manifest));
        assertTrue(source.gameDataRequests.isEmpty());
    }

    /**
     * Verifies only freshness and one synchronous refresh operation are public,
     * while the remote seams remain internal.
     */
    @Test
    void externalInterfaceHidesRemoteAndTransactionMechanics() {
        Set<String> publicMethods = Arrays.stream(GameDataRefresh.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        assertAll(
                () -> assertTrue(Modifier.isFinal(GameDataRefresh.class.getModifiers())),
                () -> assertEquals(Set.of("isDue", "refresh"), publicMethods),
                () -> assertEquals(1, GameDataRefresh.class.getConstructors().length),
                () -> assertEquals(
                        List.of(Path.class),
                        List.of(GameDataRefresh.class.getConstructors()[0].getParameterTypes())),
                () -> assertTrue(Modifier.isPublic(GameDataRefreshOutcome.class.getModifiers())),
                () -> assertTrue(Modifier.isFinal(GameDataRefreshOutcome.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(GameDataRefreshSource.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(GitHubGameDataRefreshSource.class.getModifiers())));
    }

    /**
     * Verifies an unexpected source programming error propagates instead of being
     * converted into an operational failed outcome.
     */
    @Test
    void unexpectedSourceProgrammingErrorPropagates() {
        IllegalStateException programmingFailure = new IllegalStateException("simulated source defect");
        GameDataRefresh refresh = new GameDataRefresh(tempDir, new RuntimeFailingSource(programmingFailure));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, refresh::refresh);

        assertSame(programmingFailure, thrown);
    }

    /**
     * Exercises one invalid remote manifest through the public refresh operation
     * and verifies it cannot affect live metadata or request GameData content.
     *
     * @param remoteManifest invalid remote properties text
     * @return verification failure for further diagnostic assertions
     * @throws Exception if the live manifest fixture cannot be written or inspected
     */
    private GameDataRefreshOutcome assertRejectedManifest(String remoteManifest) throws Exception {
        String liveManifest = completeManifest(MD5_ABC);
        Path manifest = tempDir.resolve("hashes.md5");
        Files.writeString(manifest, liveManifest);
        ScriptedSource source = new ScriptedSource(remoteManifest);
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source);

        GameDataRefreshOutcome outcome = refresh.refresh();

        assertEquals(GameDataRefreshOutcome.Status.FAILED, outcome.status());
        assertEquals(
                GameDataRefreshOutcome.FailureCategory.VERIFICATION,
                outcome.failureCategory().orElseThrow());
        assertTrue(outcome.diagnosticCause().isPresent());
        assertEquals(liveManifest, Files.readString(manifest));
        assertTrue(source.gameDataRequests.isEmpty());
        return outcome;
    }

    /**
     * Builds one complete hand-checked manifest for the fixed GameData filename
     * set.
     *
     * @param hash common digest fixture
     * @return UTF-8 properties text with exactly the five required entries
     */
    private static String completeManifest(String hash) {
        return "ships.csv=" + hash + "\n"
                + "renamed.csv=" + hash + "\n"
                + "events.csv=" + hash + "\n"
                + "assignments.csv=" + hash + "\n"
                + "traits.csv=" + hash + "\n";
    }

    /**
     * Serializes an otherwise complete manifest with one exact untrusted property
     * name, including platform-specific absolute paths.
     *
     * @param extraFilename additional remote property name
     * @return Java properties text retaining the exact filename
     * @throws IOException if the in-memory writer unexpectedly fails
     */
    private static String completeManifestWithExtra(String extraFilename) throws IOException {
        Properties properties = new Properties();
        for (String filename : GAME_DATA_FILENAMES) {
            properties.setProperty(filename, MD5_ABC);
        }
        properties.setProperty(extraFilename, MD5_ABC);
        StringWriter writer = new StringWriter();
        properties.store(writer, "");
        return writer.toString();
    }

    /**
     * Supplies deterministic remote bytes while recording which content the
     * module requests.
     */
    private static final class ScriptedSource implements GameDataRefreshSource {

        private final byte[] manifestBytes;
        private final IOException manifestReadFailure;
        private final List<String> gameDataRequests = new ArrayList<String>();
        private int manifestRequests;
        private int manifestCloseCount;

        /**
         * Creates a source returning the supplied UTF-8 manifest text.
         *
         * @param manifestContents complete remote response body
         */
        private ScriptedSource(String manifestContents) {
            manifestBytes = manifestContents.getBytes(StandardCharsets.UTF_8);
            manifestReadFailure = null;
        }

        /**
         * Creates a source that fails after returning every supplied manifest byte.
         *
         * @param manifestPrefix bytes available before the response fails
         * @param readFailure    checked failure raised instead of end-of-stream
         */
        private ScriptedSource(String manifestPrefix, IOException readFailure) {
            manifestBytes = manifestPrefix.getBytes(StandardCharsets.UTF_8);
            manifestReadFailure = readFailure;
        }

        /** {@inheritDoc} */
        @Override
        public InputStream openManifest() {
            manifestRequests++;
            if (manifestReadFailure != null) {
                return new FailingAfterBytesInputStream(manifestBytes, manifestReadFailure);
            }
            return new ByteArrayInputStream(manifestBytes) {
                /** {@inheritDoc} */
                @Override
                public void close() throws IOException {
                    manifestCloseCount++;
                    super.close();
                }
            };
        }

        /** {@inheritDoc} */
        @Override
        public InputStream openGameData(String filename) {
            gameDataRequests.add(filename);
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    /**
     * Returns a fixed byte prefix and then raises a deterministic acquisition
     * failure instead of signaling end-of-stream.
     */
    private static final class FailingAfterBytesInputStream extends InputStream {

        private final byte[] prefix;
        private final IOException failure;
        private int index;

        /**
         * Creates a stream that fails immediately after the supplied prefix.
         *
         * @param prefix  bytes returned successfully
         * @param failure exception raised after the prefix
         */
        private FailingAfterBytesInputStream(byte[] prefix, IOException failure) {
            this.prefix = prefix;
            this.failure = failure;
        }

        /** {@inheritDoc} */
        @Override
        public int read() throws IOException {
            if (index == prefix.length) {
                throw failure;
            }
            return prefix[index++] & 0xff;
        }
    }

    /**
     * Raises a deterministic unexpected error at the remote source boundary.
     */
    private static final class RuntimeFailingSource implements GameDataRefreshSource {

        private final RuntimeException failure;

        /**
         * Creates a source that raises the supplied programming failure.
         *
         * @param failure runtime error to propagate
         */
        private RuntimeFailingSource(RuntimeException failure) {
            this.failure = failure;
        }

        /** {@inheritDoc} */
        @Override
        public InputStream openManifest() {
            throw failure;
        }

        /** {@inheritDoc} */
        @Override
        public InputStream openGameData(String filename) {
            throw new AssertionError("GameData content must not be requested after a source defect.");
        }
    }

    /**
     * Verifies an installation without a live digest manifest remains eligible
     * for refresh.
     *
     * @throws Exception if the freshness check cannot inspect the temporary directory
     */
    @Test
    void missingManifestIsDue() throws Exception {
        GameDataRefresh refresh = new GameDataRefresh(tempDir);

        assertTrue(refresh.isDue());
    }

    /**
     * Verifies the seven-day boundary distinguishes an old manifest from a newer
     * one without introducing a test clock.
     *
     * @throws Exception if the manifest fixture cannot be written or inspected
     */
    @Test
    void manifestAgeDeterminesWhetherRefreshIsDue() throws Exception {
        Path manifest = tempDir.resolve("hashes.md5");
        Files.writeString(manifest, "ships.csv=current\n");
        GameDataRefresh refresh = new GameDataRefresh(tempDir);

        Files.setLastModifiedTime(manifest, FileTime.from(Instant.now().minus(Duration.ofDays(7))));
        assertTrue(refresh.isDue());

        Files.setLastModifiedTime(manifest, FileTime.from(Instant.now().minus(Duration.ofDays(6))));
        assertFalse(refresh.isDue());
    }
}
