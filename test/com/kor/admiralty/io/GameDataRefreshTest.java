/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.*;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Specifies the synchronous GameData Refresh contract through its external
 * interface and real temporary directories.
 */
class GameDataRefreshTest {

    private static final String MD5_ABC = "900150983cd24fb0d6963f7d28e17f72";
    private static final String MD5_OLD = "149603e6c03516362a8da23f624db945";
    private static final String UPDATED_RENAMED = "Old,New\nLegacy Enterprise,U.S.S. Enterprise\n";
    private static final Map<String, String> FIXTURE_DIGESTS = Map.of(
            "assignments.csv", "73e3b81a9994f9f92c501aac6fb40a0e",
            "events.csv", "5aa1189ca45178468edde2537b399e1f",
            "renamed.csv", "4ccfad5e9291544eaeb6de72330f1cac",
            "ships.csv", "2792e275ee4821282caf81ecd622c779",
            "traits.csv", "854c0e12f328c7a114ebfb66d71ceef8");
    private static final List<String> GAME_DATA_FILENAMES = List.of(
            "ships.csv",
            "renamed.csv",
            "events.csv",
            "assignments.csv",
            "traits.csv");

    @TempDir
    Path tempDir;

    /**
     * Waits until a refresh caller is synchronously awaiting either the blocked
     * source or the active shared attempt, without using a scheduling sleep.
     *
     * @param thread refresh caller whose wait state is observed
     */
    private static void awaitWaiting(Thread thread) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (thread.getState() != Thread.State.WAITING) {
            if (!thread.isAlive()) {
                throw new AssertionError("Refresh caller terminated before reaching the coordinated wait.");
            }
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Refresh caller did not reach the coordinated wait.");
            }
            Thread.onSpinWait();
        }
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
     * Builds a complete hand-checked manifest from per-file digest fixtures.
     *
     * @param digests digest keyed by every fixed GameData filename
     * @return complete UTF-8 properties text
     */
    private static String completeManifest(Map<String, String> digests) {
        StringBuilder manifest = new StringBuilder();
        for (String filename : GAME_DATA_FILENAMES) {
            manifest.append(filename).append('=').append(digests.get(filename)).append('\n');
        }
        return manifest.toString();
    }

    /**
     * Builds a complete manifest with one filename assigned a distinct digest.
     *
     * @param filename filename receiving the distinct digest
     * @param digest   distinct digest for that filename
     * @return complete manifest with every other entry using {@link #MD5_ABC}
     */
    private static String completeManifestWithDigest(String filename, String digest) {
        return completeManifestWithDigests(Map.of(filename, digest));
    }

    /**
     * Builds a complete manifest with selected filenames assigned distinct
     * digests.
     *
     * @param digests distinct digests keyed by fixed GameData filename
     * @return complete manifest with every other entry using {@link #MD5_ABC}
     */
    private static String completeManifestWithDigests(Map<String, String> digests) {
        String manifest = completeManifest(MD5_ABC);
        for (Map.Entry<String, String> entry : digests.entrySet()) {
            manifest = manifest.replace(
                    entry.getKey() + "=" + MD5_ABC,
                    entry.getKey() + "=" + entry.getValue());
        }
        return manifest;
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
        assertTrue(outcome.cleanupDiagnostics().isEmpty());
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
        assertTrue(outcome.cleanupDiagnostics().isEmpty());
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
        assertTrue(outcome.cleanupDiagnostics().isEmpty());
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
     * Verifies hexadecimal letter case alone does not turn matching compatibility
     * digests into changed GameData requests.
     *
     * @throws Exception if a temporary manifest cannot be written or refreshed
     */
    @Test
    void digestCaseDifferenceDoesNotRequestUnchangedFiles() throws Exception {
        Files.writeString(tempDir.resolve("hashes.md5"), completeManifest(MD5_ABC));
        ScriptedSource source = new ScriptedSource(completeManifest(MD5_ABC.toUpperCase(Locale.ROOT)));
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source);

        GameDataRefreshOutcome outcome = refresh.refresh();

        assertEquals(GameDataRefreshOutcome.Status.CURRENT, outcome.status());
        assertTrue(source.gameDataRequests.isEmpty());
    }

    /**
     * Verifies a changed refresh requests and commits only the changed GameData
     * file, publishes the validated manifest, and reports the exact immutable
     * change set.
     *
     * @throws Exception if a temporary fixture cannot be written or refreshed
     */
    @Test
    void changedRefreshRequestsOnlyChangedFileAndCommitsIt() throws Exception {
        String liveManifestContents = completeManifestWithDigest("ships.csv", MD5_OLD);
        String remoteManifestContents = completeManifest(MD5_ABC);
        Path manifest = tempDir.resolve("hashes.md5");
        Files.writeString(manifest, liveManifestContents);
        FileTime staleTime = FileTime.from(Instant.now().minus(Duration.ofDays(8)));
        Files.setLastModifiedTime(manifest, staleTime);
        writeLiveGameDataWithOldShips();
        ScriptedSource source = new ScriptedSource(
                remoteManifestContents,
                Map.of("ships.csv", "abc".getBytes(StandardCharsets.UTF_8)));
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source);

        GameDataRefreshOutcome outcome = refresh.refresh();

        assertEquals(GameDataRefreshOutcome.Status.REFRESHED, outcome.status());
        assertEquals(Set.of("ships.csv"), outcome.changedFiles());
        assertThrows(UnsupportedOperationException.class, () -> outcome.changedFiles().add("events.csv"));
        assertEquals(List.of("ships.csv"), source.gameDataRequests);
        assertEquals("abc", Files.readString(tempDir.resolve("ships.csv")));
        assertTrue(Files.getLastModifiedTime(manifest).compareTo(staleTime) > 0);
        Properties persistedManifest = new Properties();
        try (Reader reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
            persistedManifest.load(reader);
        }
        assertEquals(Set.copyOf(GAME_DATA_FILENAMES), persistedManifest.stringPropertyNames());
        for (String filename : GAME_DATA_FILENAMES) {
            assertEquals(MD5_ABC, persistedManifest.getProperty(filename));
        }
    }

    /**
     * Verifies overlapping callers on one application-owned refresh instance join
     * the same remote attempt and receive its exact immutable outcome.
     *
     * @throws Exception if deterministic thread coordination or refresh fails
     */
    @Test
    void concurrentCallersShareOneRemoteAttemptAndOutcome() throws Exception {
        String manifestContents = completeManifest(MD5_ABC);
        Files.writeString(tempDir.resolve("hashes.md5"), manifestContents);
        BlockingManifestSource source = new BlockingManifestSource(manifestContents);
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source);
        FutureTask<GameDataRefreshOutcome> firstCall = new FutureTask<GameDataRefreshOutcome>(refresh::refresh);
        AtomicBoolean joiningCallerInterrupted = new AtomicBoolean();
        FutureTask<GameDataRefreshOutcome> joiningCall = new FutureTask<GameDataRefreshOutcome>(() -> {
            try {
                return refresh.refresh();
            } finally {
                joiningCallerInterrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        Thread firstThread = new Thread(firstCall, "GameData Refresh owner");
        Thread joiningThread = new Thread(joiningCall, "GameData Refresh joiner");

        firstThread.start();
        try {
            assertTrue(source.awaitManifestRequest());
            joiningThread.start();
            awaitWaiting(joiningThread);
            joiningThread.interrupt();
        } finally {
            source.releaseManifest();
        }

        GameDataRefreshOutcome firstOutcome = firstCall.get(5, TimeUnit.SECONDS);
        GameDataRefreshOutcome joiningOutcome = joiningCall.get(5, TimeUnit.SECONDS);

        assertSame(firstOutcome, joiningOutcome);
        assertTrue(joiningCallerInterrupted.get());
        assertEquals(1, source.manifestRequests());
    }

    /**
     * Verifies the completed single-flight slot does not cache an outcome across
     * later calls on the same application-owned instance.
     *
     * @throws Exception if the temporary manifest cannot be written or refreshed
     */
    @Test
    void laterCallStartsANewRemoteAttempt() throws Exception {
        String manifestContents = completeManifest(MD5_ABC);
        Files.writeString(tempDir.resolve("hashes.md5"), manifestContents);
        ScriptedSource source = new ScriptedSource(manifestContents);
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source);

        GameDataRefreshOutcome firstOutcome = refresh.refresh();
        GameDataRefreshOutcome laterOutcome = refresh.refresh();

        assertNotSame(firstOutcome, laterOutcome);
        assertEquals(2, source.manifestRequests);
    }

    /**
     * Verifies single-flight coordination belongs to each application-owned
     * instance rather than a static or cross-directory lock.
     *
     * @throws Exception if deterministic thread coordination or refresh fails
     */
    @Test
    void separateInstancesDoNotShareAttemptCoordination() throws Exception {
        String manifestContents = completeManifest(MD5_ABC);
        Path firstDirectory = Files.createDirectory(tempDir.resolve("first"));
        Path secondDirectory = Files.createDirectory(tempDir.resolve("second"));
        Files.writeString(firstDirectory.resolve("hashes.md5"), manifestContents);
        Files.writeString(secondDirectory.resolve("hashes.md5"), manifestContents);
        BlockingManifestSource firstSource = new BlockingManifestSource(manifestContents);
        BlockingManifestSource secondSource = new BlockingManifestSource(manifestContents);
        GameDataRefresh firstRefresh = new GameDataRefresh(firstDirectory, firstSource);
        GameDataRefresh secondRefresh = new GameDataRefresh(secondDirectory, secondSource);
        FutureTask<GameDataRefreshOutcome> firstCall = new FutureTask<GameDataRefreshOutcome>(firstRefresh::refresh);
        FutureTask<GameDataRefreshOutcome> secondCall = new FutureTask<GameDataRefreshOutcome>(secondRefresh::refresh);

        new Thread(firstCall, "first GameData Refresh instance").start();
        new Thread(secondCall, "second GameData Refresh instance").start();
        try {
            assertTrue(firstSource.awaitManifestRequest());
            assertTrue(secondSource.awaitManifestRequest());
        } finally {
            firstSource.releaseManifest();
            secondSource.releaseManifest();
        }

        GameDataRefreshOutcome firstOutcome = firstCall.get(5, TimeUnit.SECONDS);
        GameDataRefreshOutcome secondOutcome = secondCall.get(5, TimeUnit.SECONDS);

        assertNotSame(firstOutcome, secondOutcome);
        assertEquals(1, firstSource.manifestRequests());
        assertEquals(1, secondSource.manifestRequests());
    }

    /**
     * Verifies interruption observed after every backup is complete is honored at
     * the final boundary before the first live replacement.
     *
     * @throws Exception if the temporary fixture or refresh thread fails
     */
    @Test
    void interruptionBeforeInstallationLeavesLiveSetUnchangedAndDue() throws Exception {
        String liveManifestContents = completeManifestWithDigest("ships.csv", MD5_OLD);
        Path manifest = tempDir.resolve("hashes.md5");
        Files.writeString(manifest, liveManifestContents);
        FileTime staleTime = FileTime.from(Instant.now().minus(Duration.ofDays(8)));
        Files.setLastModifiedTime(manifest, staleTime);
        writeLiveGameDataWithOldShips();
        ScriptedSource source = new ScriptedSource(
                completeManifest(MD5_ABC),
                Map.of("ships.csv", "abc".getBytes(StandardCharsets.UTF_8)));
        InspectingReplacement replacement = new InspectingReplacement(
                Set.of("ships.csv"),
                liveManifestContents);
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source, replacement);
        AtomicBoolean callerInterrupted = new AtomicBoolean();
        FutureTask<GameDataRefreshOutcome> refreshCall = new FutureTask<GameDataRefreshOutcome>(() -> {
            try {
                return refresh.refresh();
            } finally {
                callerInterrupted.set(Thread.currentThread().isInterrupted());
            }
        });

        new PreInstallationBoundaryInterruptingThread(
                refreshCall,
                tempDir,
                "pre-installation interrupted refresh").start();
        GameDataRefreshOutcome outcome = refreshCall.get(5, TimeUnit.SECONDS);

        assertEquals(GameDataRefreshOutcome.Status.FAILED, outcome.status());
        assertEquals(
                GameDataRefreshOutcome.FailureCategory.INSTALLATION,
                outcome.failureCategory().orElseThrow());
        assertInstanceOf(InterruptedException.class, outcome.diagnosticCause().orElseThrow());
        assertTrue(callerInterrupted.get());
        assertTrue(replacement.events.isEmpty());
        assertEquals("old", Files.readString(tempDir.resolve("ships.csv")));
        assertEquals(liveManifestContents, Files.readString(manifest));
        assertEquals(staleTime, Files.getLastModifiedTime(manifest));
        assertTrue(refresh.isDue());
        try (var entries = Files.list(tempDir)) {
            assertFalse(entries.anyMatch(path -> path.getFileName().toString().startsWith(".gamedata-refresh-")));
        }
    }

    /**
     * Verifies a caller already interrupted on entry stops at the first safe phase
     * without acquiring remote content or renewing an unchanged manifest.
     *
     * @throws Exception if the temporary fixture or refresh thread fails
     */
    @Test
    void alreadyInterruptedCallerStopsBeforeRemoteAcquisition() throws Exception {
        String manifestContents = completeManifest(MD5_ABC);
        Path manifest = tempDir.resolve("hashes.md5");
        Files.writeString(manifest, manifestContents);
        FileTime staleTime = FileTime.from(Instant.now().minus(Duration.ofDays(8)));
        Files.setLastModifiedTime(manifest, staleTime);
        ScriptedSource source = new ScriptedSource(manifestContents);
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source);
        AtomicBoolean callerInterrupted = new AtomicBoolean();
        FutureTask<GameDataRefreshOutcome> refreshCall = new FutureTask<GameDataRefreshOutcome>(() -> {
            Thread.currentThread().interrupt();
            try {
                return refresh.refresh();
            } finally {
                callerInterrupted.set(Thread.currentThread().isInterrupted());
            }
        });

        new Thread(refreshCall, "already interrupted refresh").start();
        GameDataRefreshOutcome outcome = refreshCall.get(5, TimeUnit.SECONDS);

        assertEquals(GameDataRefreshOutcome.Status.FAILED, outcome.status());
        assertEquals(
                GameDataRefreshOutcome.FailureCategory.INSTALLATION,
                outcome.failureCategory().orElseThrow());
        assertInstanceOf(InterruptedException.class, outcome.diagnosticCause().orElseThrow());
        assertTrue(callerInterrupted.get());
        assertEquals(0, source.manifestRequests);
        assertEquals(manifestContents, Files.readString(manifest));
        assertEquals(staleTime, Files.getLastModifiedTime(manifest));
        assertTrue(refresh.isDue());
    }

    /**
     * Verifies interruption after the first live replacement completes cannot
     * strand a partial transaction and remains signaled to the owning caller.
     *
     * @throws Exception if deterministic thread coordination or recovery fails
     */
    @Test
    void interruptionAfterInstallationBeginsRollsBackAndPreservesCallerState() throws Exception {
        Set<String> changedFiles = Set.of("ships.csv", "traits.csv");
        String liveManifestContents = completeManifestWithDigests(Map.of(
                "ships.csv", MD5_OLD,
                "traits.csv", MD5_OLD));
        Path manifest = tempDir.resolve("hashes.md5");
        Files.writeString(manifest, liveManifestContents);
        FileTime staleTime = FileTime.from(Instant.now().minus(Duration.ofDays(8)));
        Files.setLastModifiedTime(manifest, staleTime);
        for (String filename : GAME_DATA_FILENAMES) {
            Files.writeString(tempDir.resolve(filename), changedFiles.contains(filename) ? "old" : "abc");
        }
        ScriptedSource source = new ScriptedSource(
                completeManifest(MD5_ABC),
                Map.of(
                        "ships.csv", "abc".getBytes(StandardCharsets.UTF_8),
                        "traits.csv", "abc".getBytes(StandardCharsets.UTF_8)));
        InterruptibleInstallationReplacement replacement = new InterruptibleInstallationReplacement();
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source, replacement);
        AtomicBoolean callerInterrupted = new AtomicBoolean();
        FutureTask<GameDataRefreshOutcome> refreshCall = new FutureTask<GameDataRefreshOutcome>(() -> {
            try {
                return refresh.refresh();
            } finally {
                callerInterrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        Thread refreshThread = new Thread(refreshCall, "installation interrupted refresh");

        GameDataRefreshOutcome outcome;
        refreshThread.start();
        try {
            assertTrue(replacement.awaitFirstReplacement());
            refreshThread.interrupt();
            outcome = refreshCall.get(5, TimeUnit.SECONDS);
        } finally {
            replacement.releaseFirstReplacement();
        }

        assertEquals(GameDataRefreshOutcome.Status.FAILED, outcome.status());
        assertEquals(
                GameDataRefreshOutcome.FailureCategory.INSTALLATION,
                outcome.failureCategory().orElseThrow());
        assertInstanceOf(InterruptedException.class, outcome.diagnosticCause().orElseThrow().getCause());
        assertTrue(callerInterrupted.get());
        assertEquals("old", Files.readString(tempDir.resolve("ships.csv")));
        assertEquals("old", Files.readString(tempDir.resolve("traits.csv")));
        assertEquals(liveManifestContents, Files.readString(manifest));
        assertEquals(staleTime, Files.getLastModifiedTime(manifest));
        assertTrue(refresh.isDue());
        assertFalse(Files.exists(replacement.stagingDirectory));
    }

    /**
     * Verifies interruption observed after manifest-only staging is complete stops
     * a matching missing-manifest attempt before it publishes a live commit marker.
     *
     * @throws Exception if the temporary fixture or refresh thread fails
     */
    @Test
    void interruptionAtManifestPublicationBoundaryLeavesMissingManifestDue() throws Exception {
        copyGameDataFixtures();
        String manifestContents = completeManifest(FIXTURE_DIGESTS);
        Path manifest = tempDir.resolve("hashes.md5");
        GameDataRefresh refresh = new GameDataRefresh(tempDir, new ScriptedSource(manifestContents));
        AtomicBoolean callerInterrupted = new AtomicBoolean();
        FutureTask<GameDataRefreshOutcome> refreshCall = new FutureTask<GameDataRefreshOutcome>(() -> {
            try {
                return refresh.refresh();
            } finally {
                callerInterrupted.set(Thread.currentThread().isInterrupted());
            }
        });

        new PreInstallationBoundaryInterruptingThread(
                refreshCall,
                tempDir,
                "manifest interrupted refresh").start();
        GameDataRefreshOutcome outcome = refreshCall.get(5, TimeUnit.SECONDS);

        assertEquals(GameDataRefreshOutcome.Status.FAILED, outcome.status());
        assertEquals(
                GameDataRefreshOutcome.FailureCategory.INSTALLATION,
                outcome.failureCategory().orElseThrow());
        assertInstanceOf(InterruptedIOException.class, outcome.diagnosticCause().orElseThrow());
        assertTrue(callerInterrupted.get());
        assertFalse(Files.exists(manifest));
        assertTrue(refresh.isDue());
    }

    /**
     * Verifies every changed download passes digest verification before the first
     * live replacement and a mismatch removes all pre-installation staging.
     *
     * @throws Exception if a temporary fixture cannot be written or inspected
     */
    @Test
    void laterDigestMismatchFailsBeforeAnyLiveReplacementAndCleansStaging() throws Exception {
        Set<String> changedFiles = Set.of("ships.csv", "traits.csv");
        String liveManifestContents = completeManifestWithDigests(Map.of(
                "ships.csv", MD5_OLD,
                "traits.csv", MD5_OLD));
        Path manifest = tempDir.resolve("hashes.md5");
        Files.writeString(manifest, liveManifestContents);
        FileTime manifestTimestamp = Files.getLastModifiedTime(manifest);
        Map<String, String> liveContents = new LinkedHashMap<String, String>();
        Map<String, FileTime> liveTimestamps = new LinkedHashMap<String, FileTime>();
        for (String filename : GAME_DATA_FILENAMES) {
            String liveContent = changedFiles.contains(filename) ? "old" : "abc";
            Path liveFile = tempDir.resolve(filename);
            Files.writeString(liveFile, liveContent);
            liveContents.put(filename, liveContent);
            liveTimestamps.put(filename, Files.getLastModifiedTime(liveFile));
        }
        ScriptedSource source = new ScriptedSource(
                completeManifest(MD5_ABC),
                Map.of(
                        "ships.csv", "abc".getBytes(StandardCharsets.UTF_8),
                        "traits.csv", "wrong".getBytes(StandardCharsets.UTF_8)));
        InspectingReplacement replacement = new InspectingReplacement(changedFiles, liveManifestContents);
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source, replacement);

        GameDataRefreshOutcome outcome = refresh.refresh();

        assertEquals(GameDataRefreshOutcome.Status.FAILED, outcome.status());
        assertEquals(
                GameDataRefreshOutcome.FailureCategory.VERIFICATION,
                outcome.failureCategory().orElseThrow());
        assertTrue(outcome.diagnosticCause().orElseThrow().getMessage().contains("traits.csv"));
        assertEquals(List.of("ships.csv", "traits.csv"), source.gameDataRequests);
        assertTrue(replacement.events.isEmpty());
        assertEquals(liveManifestContents, Files.readString(manifest));
        assertEquals(manifestTimestamp, Files.getLastModifiedTime(manifest));
        for (String filename : GAME_DATA_FILENAMES) {
            Path liveFile = tempDir.resolve(filename);
            assertEquals(liveContents.get(filename), Files.readString(liveFile));
            assertEquals(liveTimestamps.get(filename), Files.getLastModifiedTime(liveFile));
        }
        try (var entries = Files.list(tempDir)) {
            assertFalse(entries.anyMatch(path -> path.getFileName().toString().startsWith(".gamedata-refresh-")));
        }
    }

    /**
     * Verifies every affected live file and the manifest are backed up before the
     * first replacement, while manifest publication remains the final move.
     *
     * @throws Exception if a temporary fixture cannot be written or refreshed
     */
    @Test
    void successfulRefreshBacksUpAffectedFilesAndPublishesManifestLast() throws Exception {
        Set<String> changedFiles = Set.of("ships.csv", "traits.csv");
        String liveManifestContents = completeManifestWithDigests(Map.of(
                "ships.csv", MD5_OLD,
                "traits.csv", MD5_OLD));
        Files.writeString(tempDir.resolve("hashes.md5"), liveManifestContents);
        for (String filename : GAME_DATA_FILENAMES) {
            Files.writeString(tempDir.resolve(filename), changedFiles.contains(filename) ? "old" : "abc");
        }
        ScriptedSource source = new ScriptedSource(
                completeManifest(MD5_ABC),
                Map.of(
                        "ships.csv", "abc".getBytes(StandardCharsets.UTF_8),
                        "traits.csv", "abc".getBytes(StandardCharsets.UTF_8)));
        InspectingReplacement replacement = new InspectingReplacement(changedFiles, liveManifestContents);
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source, replacement);

        GameDataRefreshOutcome outcome = refresh.refresh();

        assertEquals(GameDataRefreshOutcome.Status.REFRESHED, outcome.status());
        assertEquals(
                List.of("data:ships.csv", "data:traits.csv", "manifest:hashes.md5"),
                replacement.events);
        assertFalse(Files.exists(replacement.stagingDirectory));
    }

    /**
     * Verifies a mid-install failure restores an already replaced live file,
     * removes a newly introduced file, preserves the prior manifest, and cleans
     * private transaction artifacts.
     *
     * @throws Exception if a temporary fixture cannot be written or inspected
     */
    @Test
    void midInstallFailureRestoresPriorLiveSetAndRemovesNewFiles() throws Exception {
        Set<String> changedFiles = Set.of("ships.csv", "renamed.csv", "events.csv");
        String liveManifestContents = completeManifestWithDigests(Map.of(
                "ships.csv", MD5_OLD,
                "renamed.csv", MD5_OLD,
                "events.csv", MD5_OLD));
        Files.writeString(tempDir.resolve("hashes.md5"), liveManifestContents);
        for (String filename : GAME_DATA_FILENAMES) {
            if (!filename.equals("renamed.csv")) {
                Files.writeString(tempDir.resolve(filename), changedFiles.contains(filename) ? "old" : "abc");
            }
        }
        ScriptedSource source = new ScriptedSource(
                completeManifest(MD5_ABC),
                Map.of(
                        "ships.csv", "abc".getBytes(StandardCharsets.UTF_8),
                        "renamed.csv", "abc".getBytes(StandardCharsets.UTF_8),
                        "events.csv", "abc".getBytes(StandardCharsets.UTF_8)));
        IOException installationFailure = new IOException("simulated third replacement failure");
        MidInstallFailingReplacement replacement = new MidInstallFailingReplacement(installationFailure);
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source, replacement);

        GameDataRefreshOutcome outcome = refresh.refresh();

        assertEquals(GameDataRefreshOutcome.Status.FAILED, outcome.status());
        assertEquals(
                GameDataRefreshOutcome.FailureCategory.INSTALLATION,
                outcome.failureCategory().orElseThrow());
        assertSame(installationFailure, outcome.diagnosticCause().orElseThrow());
        assertTrue(outcome.recoveryDirectory().isEmpty());
        assertEquals("old", Files.readString(tempDir.resolve("ships.csv")));
        assertFalse(Files.exists(tempDir.resolve("renamed.csv")));
        assertEquals("old", Files.readString(tempDir.resolve("events.csv")));
        assertEquals(liveManifestContents, Files.readString(tempDir.resolve("hashes.md5")));
        assertFalse(Files.exists(replacement.stagingDirectory));
    }

    /**
     * Verifies a manifest-publication failure after the commit-point file moved
     * still restores every installed GameData file and the exact prior manifest.
     *
     * @throws Exception if a temporary fixture cannot be written or inspected
     */
    @Test
    void manifestPublicationFailureRestoresInstalledFilesAndPriorManifest() throws Exception {
        Set<String> changedFiles = Set.of("ships.csv", "traits.csv");
        String liveManifestContents = completeManifestWithDigests(Map.of(
                "ships.csv", MD5_OLD,
                "traits.csv", MD5_OLD));
        Files.writeString(tempDir.resolve("hashes.md5"), liveManifestContents);
        for (String filename : GAME_DATA_FILENAMES) {
            Files.writeString(tempDir.resolve(filename), changedFiles.contains(filename) ? "old" : "abc");
        }
        ScriptedSource source = new ScriptedSource(
                completeManifest(MD5_ABC),
                Map.of(
                        "ships.csv", "abc".getBytes(StandardCharsets.UTF_8),
                        "traits.csv", "abc".getBytes(StandardCharsets.UTF_8)));
        IOException publicationFailure = new IOException("simulated post-move manifest failure");
        ManifestPublicationFailingReplacement replacement =
                new ManifestPublicationFailingReplacement(publicationFailure);
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source, replacement);

        GameDataRefreshOutcome outcome = refresh.refresh();

        assertEquals(GameDataRefreshOutcome.Status.FAILED, outcome.status());
        assertEquals(
                GameDataRefreshOutcome.FailureCategory.INSTALLATION,
                outcome.failureCategory().orElseThrow());
        assertSame(publicationFailure, outcome.diagnosticCause().orElseThrow());
        assertEquals("old", Files.readString(tempDir.resolve("ships.csv")));
        assertEquals("old", Files.readString(tempDir.resolve("traits.csv")));
        assertEquals(liveManifestContents, Files.readString(tempDir.resolve("hashes.md5")));
        assertFalse(Files.exists(replacement.stagingDirectory));
    }

    /**
     * Verifies providers that reject atomic manifest overwrite still commit the
     * same refreshed result through one explicit safe replacement.
     *
     * @param failure provider-specific atomic replacement rejection
     * @throws Exception if a temporary fixture cannot be written or inspected
     */
    @ParameterizedTest
    @EnumSource(AtomicManifestFailure.class)
    void manifestPublicationFallsBackWhenAtomicReplacementIsUnavailable(
            AtomicManifestFailure failure) throws Exception {
        String liveManifestContents = completeManifestWithDigest("ships.csv", MD5_OLD);
        Files.writeString(tempDir.resolve("hashes.md5"), liveManifestContents);
        writeLiveGameDataWithOldShips();
        ScriptedSource source = new ScriptedSource(
                completeManifest(MD5_ABC),
                Map.of("ships.csv", "abc".getBytes(StandardCharsets.UTF_8)));
        AtomicReplacementRejectingReplacement replacement =
                new AtomicReplacementRejectingReplacement(failure);
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source, replacement);

        GameDataRefreshOutcome outcome = refresh.refresh();

        assertEquals(GameDataRefreshOutcome.Status.REFRESHED, outcome.status());
        assertEquals(Set.of("ships.csv"), outcome.changedFiles());
        assertEquals(1, replacement.atomicAttempts);
        assertEquals(1, replacement.explicitAttempts);
        assertEquals("abc", Files.readString(tempDir.resolve("ships.csv")));
        Properties persistedManifest = new Properties();
        try (Reader reader = Files.newBufferedReader(tempDir.resolve("hashes.md5"), StandardCharsets.UTF_8)) {
            persistedManifest.load(reader);
        }
        assertEquals(MD5_ABC, persistedManifest.getProperty("ships.csv"));
    }

    /**
     * Verifies private-artifact cleanup trouble remains warning evidence on an
     * otherwise committed refreshed outcome.
     *
     * @throws Exception if a temporary fixture cannot be written or inspected
     */
    @Test
    void refreshedOutcomeRetainsCleanupWarningsWithoutLosingCommittedSuccess() throws Exception {
        Files.writeString(
                tempDir.resolve("hashes.md5"),
                completeManifestWithDigest("ships.csv", MD5_OLD));
        writeLiveGameDataWithOldShips();
        ScriptedSource source = new ScriptedSource(
                completeManifest(MD5_ABC),
                Map.of("ships.csv", "abc".getBytes(StandardCharsets.UTF_8)));
        CleanupObstructingReplacement replacement = new CleanupObstructingReplacement();
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source, replacement);

        GameDataRefreshOutcome outcome = refresh.refresh();

        assertEquals(GameDataRefreshOutcome.Status.REFRESHED, outcome.status());
        assertEquals(Set.of("ships.csv"), outcome.changedFiles());
        assertTrue(outcome.failureCategory().isEmpty());
        assertFalse(outcome.cleanupDiagnostics().isEmpty());
        assertTrue(outcome.cleanupDiagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.getMessage().contains(replacement.obstruction.toString())));
        assertEquals("abc", Files.readString(tempDir.resolve("ships.csv")));
        assertTrue(Files.exists(tempDir.resolve("hashes.md5")));
    }

    /**
     * Verifies cleanup trouble after publishing the first validated manifest can
     * accompany a current outcome without becoming an installation failure.
     *
     * @throws Exception if a temporary fixture cannot be written or inspected
     */
    @Test
    void currentOutcomeRetainsCleanupWarningsWithoutLosingCommittedSuccess() throws Exception {
        for (String filename : GAME_DATA_FILENAMES) {
            Files.writeString(tempDir.resolve(filename), "abc");
        }
        ScriptedSource source = new ScriptedSource(completeManifest(MD5_ABC));
        CleanupObstructingReplacement replacement = new CleanupObstructingReplacement();
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source, replacement);

        GameDataRefreshOutcome outcome = refresh.refresh();

        assertEquals(GameDataRefreshOutcome.Status.CURRENT, outcome.status());
        assertTrue(outcome.changedFiles().isEmpty());
        assertTrue(outcome.failureCategory().isEmpty());
        assertFalse(outcome.cleanupDiagnostics().isEmpty());
        assertTrue(outcome.cleanupDiagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.getMessage().contains(replacement.obstruction.toString())));
        assertThrows(
                UnsupportedOperationException.class,
                () -> outcome.cleanupDiagnostics().add(new IOException("must remain immutable")));
        assertTrue(Files.exists(tempDir.resolve("hashes.md5")));
    }

    /**
     * Verifies a failed first-manifest publication restores the pre-attempt absence
     * so the reported failure remains immediately due for retry.
     *
     * @throws Exception if a temporary fixture cannot be written or inspected
     */
    @Test
    void failedCurrentManifestPublicationRestoresManifestAbsence() throws Exception {
        for (String filename : GAME_DATA_FILENAMES) {
            Files.writeString(tempDir.resolve(filename), "abc");
        }
        ScriptedSource source = new ScriptedSource(completeManifest(MD5_ABC));
        IOException publicationFailure = new IOException("simulated post-move current-manifest failure");
        CurrentManifestPublicationFailingReplacement replacement =
                new CurrentManifestPublicationFailingReplacement(publicationFailure);
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source, replacement);

        GameDataRefreshOutcome outcome = refresh.refresh();

        assertEquals(GameDataRefreshOutcome.Status.FAILED, outcome.status());
        assertEquals(
                GameDataRefreshOutcome.FailureCategory.INSTALLATION,
                outcome.failureCategory().orElseThrow());
        assertSame(publicationFailure, outcome.diagnosticCause().orElseThrow());
        assertFalse(Files.exists(tempDir.resolve("hashes.md5")));
        assertTrue(new GameDataRefresh(tempDir, source).isDue());
        assertFalse(Files.exists(replacement.stagedManifest));
    }

    /**
     * Verifies failure to restore first-manifest absence escalates to recovery and
     * retains the attempted manifest plus exact diagnostic location.
     *
     * @throws Exception if a temporary fixture cannot be written or inspected
     */
    @Test
    void incompleteCurrentManifestRecoveryRetainsEvidenceDirectory() throws Exception {
        for (String filename : GAME_DATA_FILENAMES) {
            Files.writeString(tempDir.resolve(filename), "abc");
        }
        ScriptedSource source = new ScriptedSource(completeManifest(MD5_ABC));
        IOException publicationFailure = new IOException("simulated unrecoverable manifest publication");
        UnrecoverableCurrentManifestReplacement replacement =
                new UnrecoverableCurrentManifestReplacement(publicationFailure);
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source, replacement);

        GameDataRefreshOutcome outcome = refresh.refresh();

        Path recoveryDirectory = outcome.recoveryDirectory().orElseThrow();
        Throwable diagnostic = outcome.diagnosticCause().orElseThrow();
        assertEquals(GameDataRefreshOutcome.Status.FAILED, outcome.status());
        assertEquals(
                GameDataRefreshOutcome.FailureCategory.RECOVERY,
                outcome.failureCategory().orElseThrow());
        assertTrue(Files.isDirectory(recoveryDirectory));
        assertTrue(diagnostic.getMessage().contains(recoveryDirectory.toString()));
        assertSame(publicationFailure, diagnostic.getCause());
        assertEquals(1, diagnostic.getSuppressed().length);
        assertFalse(Files.isRegularFile(tempDir.resolve("hashes.md5")));
        assertTrue(Files.readString(recoveryDirectory.resolve("hashes.md5.backup"))
                .contains("ships.csv=" + MD5_ABC));
        assertTrue(new GameDataRefresh(tempDir, source).isDue());
    }

    /**
     * Verifies incomplete rollback invalidates the live commit marker and retains
     * both precise diagnostic evidence and the remaining recovery material.
     *
     * @throws Exception if a temporary fixture cannot be written or inspected
     */
    @Test
    void incompleteRollbackRetainsRecoveryDirectoryAndPublishesNoManifest() throws Exception {
        Set<String> changedFiles = Set.of("ships.csv", "traits.csv");
        String liveManifestContents = completeManifestWithDigests(Map.of(
                "ships.csv", MD5_OLD,
                "traits.csv", MD5_OLD));
        Files.writeString(tempDir.resolve("hashes.md5"), liveManifestContents);
        for (String filename : GAME_DATA_FILENAMES) {
            Files.writeString(tempDir.resolve(filename), changedFiles.contains(filename) ? "old" : "abc");
        }
        ScriptedSource source = new ScriptedSource(
                completeManifest(MD5_ABC),
                Map.of(
                        "ships.csv", "abc".getBytes(StandardCharsets.UTF_8),
                        "traits.csv", "abc".getBytes(StandardCharsets.UTF_8)));
        IOException installationFailure = new IOException("simulated second replacement failure");
        IncompleteRecoveryReplacement replacement =
                new IncompleteRecoveryReplacement(installationFailure);
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source, replacement);

        GameDataRefreshOutcome outcome = refresh.refresh();

        Path recoveryDirectory = outcome.recoveryDirectory().orElseThrow();
        Throwable diagnostic = outcome.diagnosticCause().orElseThrow();
        assertEquals(GameDataRefreshOutcome.Status.FAILED, outcome.status());
        assertEquals(
                GameDataRefreshOutcome.FailureCategory.RECOVERY,
                outcome.failureCategory().orElseThrow());
        assertEquals(replacement.stagingDirectory.toAbsolutePath().normalize(), recoveryDirectory);
        assertTrue(diagnostic.getMessage().contains(recoveryDirectory.toString()));
        assertSame(installationFailure, diagnostic.getCause());
        assertEquals(1, diagnostic.getSuppressed().length);
        assertInstanceOf(IOException.class, diagnostic.getSuppressed()[0].getCause());
        assertFalse(Files.exists(tempDir.resolve("hashes.md5")));
        assertTrue(Files.isDirectory(recoveryDirectory));
        assertEquals("old", Files.readString(recoveryDirectory.resolve("ships.csv.backup")));
        assertEquals(liveManifestContents, Files.readString(recoveryDirectory.resolve("hashes.md5.backup")));
        assertTrue(refresh.isDue());
    }

    /**
     * Verifies an incomplete changed-file response remains a remote acquisition
     * failure and cannot leave staging or alter the live commit point.
     *
     * @throws Exception if a temporary fixture cannot be written or inspected
     */
    @Test
    void partialChangedFileReadFailsAcquisitionAndCleansStaging() throws Exception {
        String liveManifestContents = completeManifestWithDigest("ships.csv", MD5_OLD);
        Files.writeString(tempDir.resolve("hashes.md5"), liveManifestContents);
        writeLiveGameDataWithOldShips();
        IOException readFailure = new IOException("simulated partial GameData response");
        ScriptedSource source = new ScriptedSource(
                completeManifest(MD5_ABC),
                "ships.csv",
                "ab".getBytes(StandardCharsets.UTF_8),
                readFailure);
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source);

        GameDataRefreshOutcome outcome = refresh.refresh();

        assertEquals(GameDataRefreshOutcome.Status.FAILED, outcome.status());
        assertEquals(
                GameDataRefreshOutcome.FailureCategory.REMOTE_ACQUISITION,
                outcome.failureCategory().orElseThrow());
        assertSame(readFailure, outcome.diagnosticCause().orElseThrow());
        assertEquals(List.of("ships.csv"), source.gameDataRequests);
        assertEquals("old", Files.readString(tempDir.resolve("ships.csv")));
        assertEquals(liveManifestContents, Files.readString(tempDir.resolve("hashes.md5")));
        try (var entries = Files.list(tempDir)) {
            assertFalse(entries.anyMatch(path -> path.getFileName().toString().startsWith(".gamedata-refresh-")));
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
     * Verifies a missing live manifest causes real file hashes to select only the
     * changed GameData content before publishing the first validated manifest.
     *
     * @throws Exception if a temporary fixture cannot be written or refreshed
     */
    @Test
    void missingManifestHashesLiveFilesAndRequestsOnlyActualChanges() throws Exception {
        writeLiveGameDataWithOldShips();
        ScriptedSource source = new ScriptedSource(
                completeManifest(MD5_ABC),
                Map.of("ships.csv", "abc".getBytes(StandardCharsets.UTF_8)));
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source);

        GameDataRefreshOutcome outcome = refresh.refresh();

        assertEquals(GameDataRefreshOutcome.Status.REFRESHED, outcome.status());
        assertEquals(Set.of("ships.csv"), outcome.changedFiles());
        assertEquals(List.of("ships.csv"), source.gameDataRequests);
        assertEquals("abc", Files.readString(tempDir.resolve("ships.csv")));
        assertTrue(Files.exists(tempDir.resolve("hashes.md5")));
    }

    /**
     * Verifies a committed refresh changes only the next directory load and does
     * not mutate the GameData snapshot already used by the running application.
     *
     * @throws Exception if fixture data cannot be loaded or refreshed
     */
    @Test
    void committedRefreshDoesNotMutateGameDataLoadedAtStartup() throws Exception {
        copyGameDataFixtures();
        Files.writeString(tempDir.resolve("hashes.md5"), completeManifest(FIXTURE_DIGESTS));
        GameData loadedAtStartup = GameData.load(tempDir);
        Map<String, String> refreshedDigests = new LinkedHashMap<String, String>(FIXTURE_DIGESTS);
        refreshedDigests.put("renamed.csv", "edf5779581ab43bc4e3b6b4cce61f578");
        ScriptedSource source = new ScriptedSource(
                completeManifest(refreshedDigests),
                Map.of("renamed.csv", UPDATED_RENAMED.getBytes(StandardCharsets.UTF_8)));
        GameDataRefresh refresh = new GameDataRefresh(tempDir, source);

        GameDataRefreshOutcome outcome = refresh.refresh();

        assertEquals(GameDataRefreshOutcome.Status.REFRESHED, outcome.status());
        assertEquals("U.S.S. Enterprise", loadedAtStartup.ship("Enterprise Refit").getName());
        assertNull(loadedAtStartup.ship("Legacy Enterprise"));
        GameData loadedAfterRestart = GameData.load(tempDir);
        assertNull(loadedAfterRestart.ship("Enterprise Refit"));
        assertEquals("U.S.S. Enterprise", loadedAfterRestart.ship("Legacy Enterprise").getName());
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
                () -> assertFalse(Modifier.isPublic(GitHubGameDataRefreshSource.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(GameDataRefreshReplacement.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(FileSystemGameDataRefreshReplacement.class.getModifiers())),
                () -> assertEquals(
                        Set.of("replaceGameData", "replaceManifest"),
                        Arrays.stream(GameDataRefreshReplacement.class.getDeclaredMethods())
                                .map(method -> method.getName())
                                .collect(Collectors.toSet())));
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
     * Copies the complete classpath GameData fixture into the temporary live
     * directory without interpreting its bytes.
     *
     * @throws IOException if a fixture is absent or cannot be copied
     */
    private void copyGameDataFixtures() throws IOException {
        for (String filename : GAME_DATA_FILENAMES) {
            try (InputStream fixture = getClass().getResourceAsStream("/gamedata/" + filename)) {
                if (fixture == null) {
                    throw new IOException("Missing GameData test fixture: " + filename);
                }
                Files.copy(fixture, tempDir.resolve(filename));
            }
        }
    }

    /**
     * Writes the common changed-refresh fixture where Ships is stale and every
     * other fixed GameData file already contains the remote bytes.
     *
     * @throws IOException if a live fixture file cannot be written
     */
    private void writeLiveGameDataWithOldShips() throws IOException {
        for (String filename : GAME_DATA_FILENAMES) {
            Files.writeString(tempDir.resolve(filename), filename.equals("ships.csv") ? "old" : "abc");
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

    /**
     * Enumerates filesystem-provider responses that require explicit manifest
     * replacement after an atomic attempt.
     */
    private enum AtomicManifestFailure {
        ATOMIC_MOVE_NOT_SUPPORTED {
            /** {@inheritDoc} */
            @Override
            IOException create(Path source, Path target) {
                return new AtomicMoveNotSupportedException(
                        source.toString(),
                        target.toString(),
                        "simulated unsupported atomic replacement");
            }
        },
        TARGET_ALREADY_EXISTS {
            /** {@inheritDoc} */
            @Override
            IOException create(Path source, Path target) {
                return new FileAlreadyExistsException(target.toString());
            }
        };

        /**
         * Creates the provider-specific failure for one atomic replacement attempt.
         *
         * @param source staged manifest
         * @param target live manifest destination
         * @return simulated filesystem failure
         */
        abstract IOException create(Path source, Path target);
    }

    /**
     * Blocks manifest acquisition so concurrent callers overlap at the external
     * refresh interface while recording the exact remote-attempt count.
     */
    private static final class BlockingManifestSource implements GameDataRefreshSource {

        private final byte[] manifestBytes;
        private final CountDownLatch manifestRequested = new CountDownLatch(1);
        private final CountDownLatch releaseManifest = new CountDownLatch(1);
        private final AtomicInteger manifestRequests = new AtomicInteger();

        /**
         * Creates a source that blocks every manifest request until released.
         *
         * @param manifestContents complete remote response body
         */
        private BlockingManifestSource(String manifestContents) {
            manifestBytes = manifestContents.getBytes(StandardCharsets.UTF_8);
        }

        /**
         * Awaits the first attempted remote manifest acquisition.
         *
         * @return {@code true} when acquisition reached the source before the guard timeout
         * @throws InterruptedException if the test thread is interrupted while waiting
         */
        private boolean awaitManifestRequest() throws InterruptedException {
            return manifestRequested.await(5, TimeUnit.SECONDS);
        }

        /**
         * Allows all blocked manifest acquisitions to return their scripted bytes.
         */
        private void releaseManifest() {
            releaseManifest.countDown();
        }

        /**
         * Returns the number of remote manifest acquisitions observed so far.
         *
         * @return exact thread-safe request count
         */
        private int manifestRequests() {
            return manifestRequests.get();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public InputStream openManifest() throws IOException {
            manifestRequests.incrementAndGet();
            manifestRequested.countDown();
            try {
                releaseManifest.await();
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while coordinating manifest acquisition.", cause);
            }
            return new ByteArrayInputStream(manifestBytes);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public InputStream openGameData(String filename) {
            throw new AssertionError("Matching GameData must not be requested.");
        }
    }

    /**
     * Sets its real interrupt flag only when the transaction's private manifest
     * backup proves that every required backup is complete. This exercises the
     * last safe interruption check without exposing a production cancellation seam.
     */
    private static final class PreInstallationBoundaryInterruptingThread extends Thread {

        private final Path dataDirectory;

        /**
         * Creates a refresh thread that interrupts itself at the final private boundary.
         *
         * @param target        refresh task to execute
         * @param dataDirectory directory containing private transaction directories
         * @param name          diagnostic thread name
         */
        private PreInstallationBoundaryInterruptingThread(
                Runnable target,
                Path dataDirectory,
                String name) {
            super(target, name);
            this.dataDirectory = dataDirectory;
        }

        /**
         * Reports interruption while arming the signal exactly when the final
         * private manifest backup becomes observable.
         *
         * @return current interrupt state after boundary detection
         */
        @Override
        public boolean isInterrupted() {
            if (!super.isInterrupted() && hasPrivateManifestBackup()) {
                super.interrupt();
            }
            return super.isInterrupted();
        }

        /**
         * Detects the backup written last by both changed-file and manifest-only
         * pre-installation protocols.
         *
         * @return {@code true} once the transaction reaches its final private boundary
         */
        private boolean hasPrivateManifestBackup() {
            try (var entries = Files.list(dataDirectory)) {
                return entries
                        .filter(Files::isDirectory)
                        .filter(path -> path.getFileName().toString().startsWith(".gamedata-refresh-"))
                        .anyMatch(path -> Files.exists(path.resolve("hashes.md5.backup")));
            } catch (IOException cause) {
                throw new UncheckedIOException("Unable to inspect the pre-installation boundary.", cause);
            }
        }
    }

    /**
     * Completes one real live replacement, then blocks until interruption makes
     * the in-progress installation report a handled I/O failure.
     */
    private static final class InterruptibleInstallationReplacement extends RealMovingReplacement {

        private final CountDownLatch firstReplacementCompleted = new CountDownLatch(1);
        private final CountDownLatch releaseFirstReplacement = new CountDownLatch(1);
        private int gameDataReplacements;
        private Path stagingDirectory;

        /**
         * Awaits the point after the first staged file has become live.
         *
         * @return {@code true} when installation reached the boundary before the guard timeout
         * @throws InterruptedException if the test thread is interrupted while waiting
         */
        private boolean awaitFirstReplacement() throws InterruptedException {
            return firstReplacementCompleted.await(5, TimeUnit.SECONDS);
        }

        /**
         * Releases the coordinated replacement if the test exits before interrupting it.
         */
        private void releaseFirstReplacement() {
            releaseFirstReplacement.countDown();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void replaceGameData(Path source, Path target) throws IOException {
            stagingDirectory = source.getParent();
            gameDataReplacements++;
            super.replaceGameData(source, target);
            if (gameDataReplacements != 1) {
                return;
            }
            firstReplacementCompleted.countDown();
            try {
                releaseFirstReplacement.await();
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted after live installation began.", cause);
            }
        }
    }

    /**
     * Supplies deterministic remote bytes while recording which content the
     * module requests.
     */
    private static final class ScriptedSource implements GameDataRefreshSource {

        private final byte[] manifestBytes;
        private final IOException manifestReadFailure;
        private final Map<String, byte[]> gameData;
        private final Map<String, IOException> gameDataReadFailures;
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
            gameData = Map.of();
            gameDataReadFailures = Map.of();
        }

        /**
         * Creates a source returning the supplied manifest and selected GameData
         * content.
         *
         * @param manifestContents complete remote response body
         * @param gameData         content keyed by module-selected filename
         */
        private ScriptedSource(String manifestContents, Map<String, byte[]> gameData) {
            manifestBytes = manifestContents.getBytes(StandardCharsets.UTF_8);
            manifestReadFailure = null;
            this.gameData = Map.copyOf(gameData);
            gameDataReadFailures = Map.of();
        }

        /**
         * Creates a source whose selected GameData response fails after a fixed
         * byte prefix.
         *
         * @param manifestContents complete remote response body
         * @param filename         module-selected file whose response fails
         * @param prefix           bytes returned before the failure
         * @param readFailure      checked failure raised instead of end-of-stream
         */
        private ScriptedSource(
                String manifestContents,
                String filename,
                byte[] prefix,
                IOException readFailure) {
            manifestBytes = manifestContents.getBytes(StandardCharsets.UTF_8);
            manifestReadFailure = null;
            gameData = Map.of(filename, prefix.clone());
            gameDataReadFailures = Map.of(filename, readFailure);
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
            gameData = Map.of();
            gameDataReadFailures = Map.of();
        }

        /**
         * {@inheritDoc}
         */
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

        /**
         * {@inheritDoc}
         */
        @Override
        public InputStream openGameData(String filename) {
            gameDataRequests.add(filename);
            byte[] bytes = gameData.getOrDefault(filename, new byte[0]);
            IOException readFailure = gameDataReadFailures.get(filename);
            if (readFailure != null) {
                return new FailingAfterBytesInputStream(bytes, readFailure);
            }
            return new ByteArrayInputStream(bytes);
        }
    }

    /**
     * Keeps ordinary replacement behavior on the real temporary filesystem so
     * focused adapters override only the fault or observation under test.
     */
    private static class RealMovingReplacement implements GameDataRefreshReplacement {

        /**
         * {@inheritDoc}
         */
        @Override
        public void replaceGameData(Path source, Path target) throws IOException {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void replaceManifest(Path source, Path target, CopyOption... options) throws IOException {
            Files.move(source, target, options);
        }
    }

    /**
     * Observes only the replacement boundary while leaving ordinary move behavior
     * to the real temporary filesystem.
     */
    private static final class InspectingReplacement extends RealMovingReplacement {

        private final Set<String> changedFiles;
        private final String liveManifestContents;
        private final List<String> events = new ArrayList<String>();
        private Path stagingDirectory;

        /**
         * Creates an adapter that checks the complete backup set at the first live
         * replacement.
         *
         * @param changedFiles         affected live filenames
         * @param liveManifestContents exact manifest bytes expected in backup
         */
        private InspectingReplacement(Set<String> changedFiles, String liveManifestContents) {
            this.changedFiles = Set.copyOf(changedFiles);
            this.liveManifestContents = liveManifestContents;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void replaceGameData(Path source, Path target) throws IOException {
            stagingDirectory = source.getParent();
            if (events.isEmpty()) {
                for (String filename : changedFiles) {
                    assertEquals("old", Files.readString(stagingDirectory.resolve(filename + ".backup")));
                }
                assertEquals(
                        liveManifestContents,
                        Files.readString(stagingDirectory.resolve("hashes.md5.backup")));
            }
            events.add("data:" + target.getFileName());
            super.replaceGameData(source, target);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void replaceManifest(Path source, Path target, CopyOption... options) throws IOException {
            events.add("manifest:" + target.getFileName());
            super.replaceManifest(source, target, options);
        }
    }

    /**
     * Rejects atomic manifest replacement while permitting the explicit fallback
     * and all GameData moves on the real temporary filesystem.
     */
    private static final class AtomicReplacementRejectingReplacement extends RealMovingReplacement {

        private final AtomicManifestFailure failure;
        private int atomicAttempts;
        private int explicitAttempts;

        /**
         * Creates an adapter for one provider-specific atomic move failure.
         *
         * @param failure atomic replacement response to simulate
         */
        private AtomicReplacementRejectingReplacement(AtomicManifestFailure failure) {
            this.failure = failure;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void replaceManifest(Path source, Path target, CopyOption... options) throws IOException {
            List<CopyOption> requestedOptions = Arrays.asList(options);
            if (requestedOptions.contains(StandardCopyOption.ATOMIC_MOVE)) {
                atomicAttempts++;
                throw failure.create(source, target);
            }
            assertEquals(List.of(StandardCopyOption.REPLACE_EXISTING), requestedOptions);
            explicitAttempts++;
            super.replaceManifest(source, target, options);
        }
    }

    /**
     * Completes every replacement and then leaves one non-empty private path that
     * ordinary cleanup cannot remove.
     */
    private static final class CleanupObstructingReplacement extends RealMovingReplacement {

        private Path obstruction;

        /**
         * {@inheritDoc}
         */
        @Override
        public void replaceManifest(Path source, Path target, CopyOption... options) throws IOException {
            super.replaceManifest(source, target, options);
            obstruction = source;
            Files.createDirectory(obstruction);
            Files.writeString(obstruction.resolve("retained.txt"), "simulated cleanup obstruction");
        }
    }

    /**
     * Moves a first validated manifest into place and then reports a deterministic
     * publication failure.
     */
    private static final class CurrentManifestPublicationFailingReplacement
            extends RealMovingReplacement {

        private final IOException failure;
        private Path stagedManifest;

        /**
         * Creates an adapter with one post-move current-manifest fault.
         *
         * @param failure checked publication failure to report
         */
        private CurrentManifestPublicationFailingReplacement(IOException failure) {
            this.failure = failure;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void replaceManifest(Path source, Path target, CopyOption... options) throws IOException {
            stagedManifest = source;
            super.replaceManifest(source, target, options);
            throw failure;
        }
    }

    /**
     * Replaces the first manifest with a non-empty path that cannot be removed by
     * ordinary rollback, forcing retained recovery evidence.
     */
    private static final class UnrecoverableCurrentManifestReplacement
            extends RealMovingReplacement {

        private final IOException failure;

        /**
         * Creates an adapter with one publication fault after an obstructing side effect.
         *
         * @param failure checked publication failure to report
         */
        private UnrecoverableCurrentManifestReplacement(IOException failure) {
            this.failure = failure;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void replaceManifest(Path source, Path target, CopyOption... options) throws IOException {
            super.replaceManifest(source, target, options);
            Files.delete(target);
            Files.createDirectory(target);
            Files.writeString(target.resolve("blocked.txt"), "simulated manifest rollback obstruction");
            Path quarantineObstruction = source.getParent().resolve("hashes.md5.failed");
            Files.createDirectory(quarantineObstruction);
            Files.writeString(quarantineObstruction.resolve("blocked.txt"), "simulated quarantine obstruction");
            throw failure;
        }
    }

    /**
     * Uses real moves until the third live-file replacement fails, leaving the
     * module responsible for restoring every affected destination.
     */
    private static final class MidInstallFailingReplacement extends RealMovingReplacement {

        private final IOException failure;
        private int gameDataReplacements;
        private Path stagingDirectory;

        /**
         * Creates a replacement adapter with one deterministic installation fault.
         *
         * @param failure checked failure raised by the third GameData replacement
         */
        private MidInstallFailingReplacement(IOException failure) {
            this.failure = failure;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void replaceGameData(Path source, Path target) throws IOException {
            stagingDirectory = source.getParent();
            gameDataReplacements++;
            if (gameDataReplacements == 3) {
                throw failure;
            }
            super.replaceGameData(source, target);
        }
    }

    /**
     * Publishes the staged manifest and then reports a deterministic failure so
     * rollback must replace an already changed commit point.
     */
    private static final class ManifestPublicationFailingReplacement extends RealMovingReplacement {

        private final IOException failure;
        private Path stagingDirectory;

        /**
         * Creates a replacement adapter with one post-move publication fault.
         *
         * @param failure checked failure reported after publishing the new manifest
         */
        private ManifestPublicationFailingReplacement(IOException failure) {
            this.failure = failure;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void replaceGameData(Path source, Path target) throws IOException {
            stagingDirectory = source.getParent();
            super.replaceGameData(source, target);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void replaceManifest(Path source, Path target, CopyOption... options) throws IOException {
            super.replaceManifest(source, target, options);
            if (source.getFileName().toString().equals("hashes.md5")) {
                throw failure;
            }
        }
    }

    /**
     * Fails one forward replacement after obstructing restoration. The backup
     * branch makes a destructive rollback implementation consume its evidence,
     * while source-preserving recovery bypasses that branch and retains the copy.
     */
    private static final class IncompleteRecoveryReplacement extends RealMovingReplacement {

        private final IOException installationFailure;
        private int forwardReplacements;
        private Path stagingDirectory;

        /**
         * Creates an adapter with a deterministic installation fault that leaves a
         * real filesystem obstruction for rollback.
         *
         * @param installationFailure checked failure raised by the second forward move
         */
        private IncompleteRecoveryReplacement(IOException installationFailure) {
            this.installationFailure = installationFailure;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void replaceGameData(Path source, Path target) throws IOException {
            stagingDirectory = source.getParent();
            if (source.getFileName().toString().equals("ships.csv.backup")) {
                Files.deleteIfExists(source);
                throw new IOException("simulated destructive restoration failure");
            }
            if (!source.getFileName().toString().endsWith(".backup")) {
                forwardReplacements++;
                if (forwardReplacements == 2) {
                    Path obstructedRestore = target.getParent().resolve("ships.csv");
                    Files.deleteIfExists(obstructedRestore);
                    Files.createDirectory(obstructedRestore);
                    Files.writeString(obstructedRestore.resolve("blocked.txt"), "simulated restore obstruction");
                    throw installationFailure;
                }
            }
            super.replaceGameData(source, target);
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

        /**
         * {@inheritDoc}
         */
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
    private record RuntimeFailingSource(RuntimeException failure) implements GameDataRefreshSource {

        /**
         * Creates a source that raises the supplied programming failure.
         *
         * @param failure runtime error to propagate
         */
        private RuntimeFailingSource {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public InputStream openManifest() {
            throw failure;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public InputStream openGameData(String filename) {
            throw new AssertionError("GameData content must not be requested after a source defect.");
        }
    }
}
