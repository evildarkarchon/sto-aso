/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty.io;

import com.kor.admiralty.Globals;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

/**
 * Determines freshness and synchronously refreshes the GameData files beneath
 * one already-resolved application data directory.
 */
public final class GameDataRefresh {

    private static final long REFRESH_INTERVAL_MILLIS = Duration.ofDays(7).toMillis();
    private static final String STAGING_PREFIX = ".gamedata-refresh-";
    private static final String BACKUP_SUFFIX = ".backup";
    private static final String FAILED_MANIFEST_SUFFIX = ".failed";
    private static final String RECOVERY_MARKER = ".recovery-required";
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
    private final GameDataRefreshReplacement replacement;
    private final Object attemptMonitor = new Object();
    // Guarded by attemptMonitor so only callers of this application-owned instance share work.
    private CompletableFuture<GameDataRefreshOutcome> activeAttempt;
    private volatile boolean recoveryRequired;

    /**
     * Creates a GameData Refresh rooted in an already-resolved data directory.
     *
     * @param dataDirectory directory containing the live GameData set
     */
    public GameDataRefresh(Path dataDirectory) {
        this(
                dataDirectory,
                new GitHubGameDataRefreshSource(),
                new FileSystemGameDataRefreshReplacement());
    }

    /**
     * Creates a refresh with an internal content source for deterministic package
     * tests and production adapters.
     *
     * @param dataDirectory directory containing the live GameData set
     * @param source        remote content source that cannot select local paths
     */
    GameDataRefresh(Path dataDirectory, GameDataRefreshSource source) {
        this(dataDirectory, source, new FileSystemGameDataRefreshReplacement());
    }

    /**
     * Creates a refresh with internal content and replacement adapters for
     * deterministic package tests.
     *
     * @param dataDirectory directory containing the live GameData set
     * @param source        remote content source that cannot select local paths
     * @param replacement   adapter limited to live-file and manifest replacement
     */
    GameDataRefresh(
            Path dataDirectory,
            GameDataRefreshSource source,
            GameDataRefreshReplacement replacement) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.source = Objects.requireNonNull(source, "source");
        this.replacement = Objects.requireNonNull(replacement, "replacement");
        manifest = dataDirectory.resolve(Globals.FILENAME_HASHES);
    }

    /**
     * Waits uninterruptibly for the application-owned attempt so a joining caller
     * cannot cancel work shared with other callers. Any existing interruption
     * state remains set for the caller.
     *
     * @param attempt active attempt published by this instance
     * @return exact immutable outcome completed by the attempt owner
     */
    private static GameDataRefreshOutcome joinAttempt(CompletableFuture<GameDataRefreshOutcome> attempt) {
        try {
            return attempt.join();
        } catch (CompletionException wrapper) {
            Throwable cause = wrapper.getCause();
            if (cause instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw wrapper;
        }
    }

    /**
     * Selects the fixed GameData files whose remote digests differ from the live
     * manifest or calculated live hashes.
     *
     * @param localManifest  live or calculated local digests
     * @param remoteManifest validated remote digests
     * @return changed filenames in stable installation order
     */
    private static List<String> findChangedFiles(Properties localManifest, Properties remoteManifest) {
        List<String> changedFiles = new ArrayList<String>();
        for (String filename : GAME_DATA_FILENAMES) {
            String localDigest = localManifest.getProperty(filename);
            String remoteDigest = remoteManifest.getProperty(filename);
            if (!remoteDigest.equalsIgnoreCase(localDigest)) {
                changedFiles.add(filename);
            }
        }
        return changedFiles;
    }

    /**
     * Creates the operational result for an interruption observed while all work
     * is still private and no live replacement needs recovery. Inspecting the flag
     * rather than clearing it preserves the interruption signal for the caller.
     *
     * @return failed installation outcome carrying interruption diagnostics
     */
    private static GameDataRefreshOutcome interruptedBeforeInstallation() {
        return GameDataRefreshOutcome.failed(
                GameDataRefreshOutcome.FailureCategory.INSTALLATION,
                new InterruptedException("GameData Refresh interrupted before live installation."),
                null);
    }

    /**
     * Resolves the private backup location for one fixed live filename.
     *
     * @param stagingDirectory private transaction directory
     * @param filename         fixed live filename
     * @return sibling backup path within the staging directory
     */
    private static Path backupPath(Path stagingDirectory, String filename) {
        return stagingDirectory.resolve(filename + BACKUP_SUFFIX);
    }

    /**
     * Resolves the retained location for a manifest removed from the live commit
     * point during recovery.
     *
     * @param stagingDirectory private transaction directory
     * @return quarantined manifest path
     */
    private static Path failedManifestPath(Path stagingDirectory) {
        return stagingDirectory.resolve(Globals.FILENAME_HASHES + FAILED_MANIFEST_SUFFIX);
    }

    /**
     * Best-effort removes files owned by one private refresh attempt. Cleanup
     * diagnostics do not replace the transaction's more useful outcome.
     *
     * @param stagingDirectory private directory created by this refresh attempt
     * @return immutable path-specific diagnostics for cleanup operations that failed
     */
    private static List<Throwable> deleteStagingDirectory(Path stagingDirectory) {
        List<Throwable> cleanupDiagnostics = new ArrayList<Throwable>();
        for (String filename : GAME_DATA_FILENAMES) {
            deleteStagingPath(stagingDirectory.resolve(filename), cleanupDiagnostics);
            deleteStagingPath(backupPath(stagingDirectory, filename), cleanupDiagnostics);
        }
        deleteStagingPath(stagingDirectory.resolve(Globals.FILENAME_HASHES), cleanupDiagnostics);
        deleteStagingPath(backupPath(stagingDirectory, Globals.FILENAME_HASHES), cleanupDiagnostics);
        deleteStagingPath(failedManifestPath(stagingDirectory), cleanupDiagnostics);
        deleteStagingPath(stagingDirectory.resolve(RECOVERY_MARKER), cleanupDiagnostics);
        deleteStagingPath(stagingDirectory, cleanupDiagnostics);
        return List.copyOf(cleanupDiagnostics);
    }

    /**
     * Deletes one staging path without converting cleanup trouble into a refresh
     * failure.
     *
     * @param path               staging path owned by the current attempt
     * @param cleanupDiagnostics mutable collection receiving path-specific failures
     */
    private static void deleteStagingPath(Path path, List<Throwable> cleanupDiagnostics) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException cause) {
            cleanupDiagnostics.add(new IOException(
                    "Unable to remove GameData Refresh staging path: " + path + ".",
                    cause));
        }
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
     * Calculates the lower-case MD5 digest required by the shipped manifest format.
     *
     * @param file GameData file to hash
     * @return lower-case hexadecimal digest
     * @throws IOException              if the file cannot be read
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
     * Stops a manifest-only transaction while all artifacts remain private. The
     * interrupt flag is inspected without clearing it so the caller retains the
     * signal after cleanup completes.
     *
     * @throws InterruptedIOException when the owning refresh thread is interrupted
     */
    private static void throwIfInterruptedBeforeInstallation() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("GameData Refresh interrupted before live installation.");
        }
    }

    /**
     * Reports whether the live digest manifest is missing or at least seven days
     * old.
     *
     * @return {@code true} when a refresh should be attempted
     * @throws IOException if manifest metadata or retained recovery evidence cannot be inspected
     */
    public boolean isDue() throws IOException {
        return recoveryRequired
                || hasRetainedRecovery()
                || Files.notExists(manifest)
                || Files.getLastModifiedTime(manifest).toMillis()
                <= System.currentTimeMillis() - REFRESH_INTERVAL_MILLIS;
    }

    /**
     * Detects handled recovery evidence retained by an earlier application
     * instance so a recent but uncommitted manifest path cannot suppress retry.
     *
     * @return {@code true} when a private recovery directory carries its marker
     * @throws IOException if the data directory cannot be inspected
     */
    private boolean hasRetainedRecovery() throws IOException {
        if (Files.notExists(dataDirectory)) {
            return false;
        }
        try (var entries = Files.list(dataDirectory)) {
            return entries
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(STAGING_PREFIX))
                    .anyMatch(path -> Files.exists(path.resolve(RECOVERY_MARKER)));
        }
    }

    /**
     * Performs or joins one synchronous refresh attempt and returns its immutable
     * outcome. Concurrent callers on this instance share the active attempt;
     * expected operational failures are categorized, while unexpected programming
     * failures propagate to every caller.
     *
     * @return current, refreshed, or failed outcome for this attempt
     */
    public GameDataRefreshOutcome refresh() {
        CompletableFuture<GameDataRefreshOutcome> attempt;
        boolean attemptOwner;
        synchronized (attemptMonitor) {
            attempt = activeAttempt;
            attemptOwner = attempt == null;
            if (attemptOwner) {
                attempt = new CompletableFuture<GameDataRefreshOutcome>();
                activeAttempt = attempt;
            }
        }

        if (!attemptOwner) {
            return joinAttempt(attempt);
        }

        try {
            GameDataRefreshOutcome outcome = performRefresh();
            synchronized (attemptMonitor) {
                activeAttempt = null;
                attempt.complete(outcome);
            }
            return outcome;
        } catch (RuntimeException | Error cause) {
            synchronized (attemptMonitor) {
                activeAttempt = null;
                attempt.completeExceptionally(cause);
            }
            throw cause;
        }
    }

    /**
     * Executes the remote and filesystem transaction for the caller that owns the
     * current single-flight attempt.
     *
     * @return current, refreshed, or failed outcome for the owned attempt
     */
    private GameDataRefreshOutcome performRefresh() {
        if (Thread.currentThread().isInterrupted()) {
            return interruptedBeforeInstallation();
        }

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
        if (Thread.currentThread().isInterrupted()) {
            return interruptedBeforeInstallation();
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
        if (Thread.currentThread().isInterrupted()) {
            return interruptedBeforeInstallation();
        }

        List<String> changedFiles = findChangedFiles(localManifest, remoteManifest);
        if (Thread.currentThread().isInterrupted()) {
            return interruptedBeforeInstallation();
        }
        if (!changedFiles.isEmpty()) {
            return refreshChangedFiles(remoteManifest, changedFiles);
        }

        try {
            return GameDataRefreshOutcome.current()
                    .withCleanupDiagnostics(renewManifest(remoteManifest));
        } catch (ManifestRecoveryException cause) {
            return GameDataRefreshOutcome.failed(
                    GameDataRefreshOutcome.FailureCategory.RECOVERY,
                    cause,
                    cause.recoveryDirectory());
        } catch (IOException cause) {
            return GameDataRefreshOutcome.failed(
                    GameDataRefreshOutcome.FailureCategory.INSTALLATION,
                    cause,
                    null);
        }
    }

    /**
     * Acquires changed files privately, replaces them, and publishes the validated
     * manifest last as the refresh commit point.
     *
     * @param remoteManifest validated remote digest manifest
     * @param changedFiles   fixed filenames selected by digest comparison
     * @return refreshed outcome, or a categorized operational failure
     */
    private GameDataRefreshOutcome refreshChangedFiles(Properties remoteManifest, List<String> changedFiles) {
        Path stagingDirectory;
        try {
            stagingDirectory = Files.createTempDirectory(dataDirectory, STAGING_PREFIX);
        } catch (IOException cause) {
            return GameDataRefreshOutcome.failed(
                    GameDataRefreshOutcome.FailureCategory.INSTALLATION,
                    cause,
                    null);
        }
        if (Thread.currentThread().isInterrupted()) {
            return interruptedBeforeInstallation()
                    .withCleanupDiagnostics(deleteStagingDirectory(stagingDirectory));
        }

        boolean retainStagingDirectory = false;
        boolean installationStarted = false;
        Set<String> existingFiles = Set.of();
        boolean manifestExisted = Files.exists(manifest);
        GameDataRefreshOutcome outcome = null;
        try {
            outcome = stageChangedFiles(stagingDirectory, remoteManifest, changedFiles);
            if (outcome == null && Thread.currentThread().isInterrupted()) {
                outcome = interruptedBeforeInstallation();
            }
            if (outcome == null) {
                Path stagedManifest = stagingDirectory.resolve(Globals.FILENAME_HASHES);
                try (Writer writer = Files.newBufferedWriter(stagedManifest, StandardCharsets.UTF_8)) {
                    remoteManifest.store(writer, "");
                }
                existingFiles = backupExistingFiles(stagingDirectory, changedFiles);
                if (Thread.currentThread().isInterrupted()) {
                    outcome = interruptedBeforeInstallation();
                } else {
                    installationStarted = true;
                    for (String filename : changedFiles) {
                        replacement.replaceGameData(
                                stagingDirectory.resolve(filename),
                                dataDirectory.resolve(filename));
                    }
                    // Readers treat the manifest as the commit marker, so it cannot describe
                    // the new set until every changed GameData file is already in place.
                    publishManifest(stagedManifest, manifest);
                    outcome = GameDataRefreshOutcome.refreshed(Set.copyOf(changedFiles));
                }
            }
        } catch (IOException cause) {
            boolean restoreInterruption = installationStarted && Thread.interrupted();
            try {
                if (installationStarted) {
                    // Rollback must not inherit cancellation state from the failed forward move.
                    List<Throwable> recoveryFailures = restorePreviousFiles(
                            stagingDirectory,
                            changedFiles,
                            existingFiles,
                            manifestExisted);
                    if (!recoveryFailures.isEmpty()) {
                        retainStagingDirectory = true;
                        Path recoveryDirectory = stagingDirectory.toAbsolutePath().normalize();
                        recordRecoveryRequired(stagingDirectory, recoveryFailures);
                        outcome = GameDataRefreshOutcome.failed(
                                GameDataRefreshOutcome.FailureCategory.RECOVERY,
                                new RecoveryException(recoveryDirectory, cause, recoveryFailures),
                                recoveryDirectory);
                    }
                }
                if (!retainStagingDirectory) {
                    outcome = GameDataRefreshOutcome.failed(
                            GameDataRefreshOutcome.FailureCategory.INSTALLATION,
                            cause,
                            null);
                }
            } finally {
                if (restoreInterruption) {
                    Thread.currentThread().interrupt();
                }
            }
        } finally {
            if (outcome == null && !installationStarted) {
                // Unexpected pre-installation failures still release private artifacts.
                deleteStagingDirectory(stagingDirectory);
            }
        }

        if (retainStagingDirectory) {
            return outcome;
        }
        return outcome.withCleanupDiagnostics(deleteStagingDirectory(stagingDirectory));
    }

    /**
     * Acquires and verifies every changed file before installation can affect a
     * live destination.
     *
     * @param stagingDirectory private directory receiving remote content
     * @param remoteManifest   validated remote digest manifest
     * @param changedFiles     fixed filenames selected for acquisition
     * @return categorized failure, or {@code null} when every file is verified
     */
    private GameDataRefreshOutcome stageChangedFiles(
            Path stagingDirectory,
            Properties remoteManifest,
            List<String> changedFiles) {
        for (String filename : changedFiles) {
            if (Thread.currentThread().isInterrupted()) {
                return interruptedBeforeInstallation();
            }
            Path stagedFile = stagingDirectory.resolve(filename);
            try (InputStream input = source.openGameData(filename)) {
                if (Thread.currentThread().isInterrupted()) {
                    return interruptedBeforeInstallation();
                }
                Files.copy(input, stagedFile);
            } catch (IOException cause) {
                return GameDataRefreshOutcome.failed(
                        GameDataRefreshOutcome.FailureCategory.REMOTE_ACQUISITION,
                        cause,
                        null);
            }
            if (Thread.currentThread().isInterrupted()) {
                return interruptedBeforeInstallation();
            }

            try {
                String stagedHash = calculateHash(stagedFile);
                String expectedHash = remoteManifest.getProperty(filename);
                if (!expectedHash.equalsIgnoreCase(stagedHash)) {
                    return GameDataRefreshOutcome.failed(
                            GameDataRefreshOutcome.FailureCategory.VERIFICATION,
                            new DigestMismatchException(filename, expectedHash, stagedHash),
                            null);
                }
            } catch (IOException | NoSuchAlgorithmException cause) {
                return GameDataRefreshOutcome.failed(
                        GameDataRefreshOutcome.FailureCategory.VERIFICATION,
                        cause,
                        null);
            }
        }
        return null;
    }

    /**
     * Copies every existing affected live file and manifest into the private
     * staging directory before replacement begins.
     *
     * @param stagingDirectory private transaction directory
     * @param changedFiles     fixed filenames that will be replaced
     * @return filenames whose live versions existed before installation
     * @throws IOException if any required recovery copy cannot be completed
     */
    private Set<String> backupExistingFiles(Path stagingDirectory, List<String> changedFiles) throws IOException {
        Set<String> existingFiles = new HashSet<String>();
        for (String filename : changedFiles) {
            Path liveFile = dataDirectory.resolve(filename);
            if (Files.exists(liveFile)) {
                Files.copy(
                        liveFile,
                        backupPath(stagingDirectory, filename),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                existingFiles.add(filename);
            }
        }
        if (Files.exists(manifest)) {
            Files.copy(
                    manifest,
                    backupPath(stagingDirectory, Globals.FILENAME_HASHES),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
        }
        return existingFiles;
    }

    /**
     * Restores the complete pre-installation file set after a caught replacement
     * failure. Every affected file is attempted so one recovery fault does not
     * prevent independent files from being restored.
     *
     * @param stagingDirectory private directory containing recovery copies
     * @param changedFiles     files included in the attempted installation
     * @param existingFiles    files that existed before installation
     * @param manifestExisted  whether the prior live manifest existed
     * @return diagnostic failures encountered while restoring or invalidating files
     */
    private List<Throwable> restorePreviousFiles(
            Path stagingDirectory,
            List<String> changedFiles,
            Set<String> existingFiles,
            boolean manifestExisted) {
        List<Throwable> recoveryFailures = new ArrayList<Throwable>();
        for (String filename : changedFiles) {
            Path liveFile = dataDirectory.resolve(filename);
            try {
                if (existingFiles.contains(filename)) {
                    Files.copy(
                            backupPath(stagingDirectory, filename),
                            liveFile,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                } else {
                    Files.deleteIfExists(liveFile);
                }
            } catch (IOException cause) {
                recoveryFailures.add(new IOException(
                        "Unable to restore GameData file " + liveFile
                                + " from " + backupPath(stagingDirectory, filename) + ".",
                        cause));
            }
        }

        if (recoveryFailures.isEmpty()) {
            try {
                if (manifestExisted) {
                    Files.copy(
                            backupPath(stagingDirectory, Globals.FILENAME_HASHES),
                            manifest,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                } else {
                    restoreManifestAbsence(stagingDirectory);
                }
            } catch (IOException cause) {
                recoveryFailures.add(new IOException(
                        "Unable to restore the GameData manifest " + manifest
                                + " from " + backupPath(stagingDirectory, Globals.FILENAME_HASHES) + ".",
                        cause));
                invalidateManifest(stagingDirectory, recoveryFailures);
            }
        } else {
            invalidateManifest(stagingDirectory, recoveryFailures);
        }
        return List.copyOf(recoveryFailures);
    }

    /**
     * Removes the live commit marker when a complete prior GameData set could not
     * be restored.
     *
     * @param stagingDirectory private directory retaining recovery evidence
     * @param recoveryFailures mutable diagnostic collection for this rollback
     */
    private void invalidateManifest(Path stagingDirectory, List<Throwable> recoveryFailures) {
        try {
            restoreManifestAbsence(stagingDirectory);
        } catch (IOException cause) {
            recoveryFailures.add(new IOException(
                    "Unable to invalidate the GameData manifest at " + manifest + ".",
                    cause));
        }
    }

    /**
     * Removes a possibly published manifest from the live commit point while
     * retaining its bytes as recovery evidence when a move can succeed.
     *
     * @param stagingDirectory private transaction directory
     * @throws IOException if neither quarantine nor deletion restores absence
     */
    private void restoreManifestAbsence(Path stagingDirectory) throws IOException {
        if (Files.notExists(manifest)) {
            return;
        }
        try {
            Files.move(
                    manifest,
                    failedManifestPath(stagingDirectory),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException quarantineFailure) {
            try {
                Files.deleteIfExists(manifest);
            } catch (IOException deletionFailure) {
                quarantineFailure.addSuppressed(deletionFailure);
                throw quarantineFailure;
            }
        }
    }

    /**
     * Persists a private marker and an in-instance fast path for incomplete
     * recovery so freshness remains due until manual recovery is completed.
     *
     * @param stagingDirectory private directory retained for recovery
     * @param recoveryFailures mutable diagnostic collection for marker failures
     */
    private void recordRecoveryRequired(Path stagingDirectory, List<Throwable> recoveryFailures) {
        recoveryRequired = true;
        try {
            Files.writeString(stagingDirectory.resolve(RECOVERY_MARKER), "");
        } catch (IOException cause) {
            recoveryFailures.add(new IOException(
                    "Unable to mark the retained GameData recovery directory " + stagingDirectory + ".",
                    cause));
        }
    }

    /**
     * Publishes validated metadata atomically when the filesystem supports it and
     * retries with explicit replacement for providers that reject atomic overwrite.
     *
     * @param source complete staged manifest
     * @param target live manifest commit point
     * @throws IOException if neither replacement mode can publish the manifest
     */
    private void publishManifest(Path source, Path target) throws IOException {
        try {
            replacement.replaceManifest(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException | FileAlreadyExistsException cause) {
            // Some providers reject atomic overwrite even though explicit replacement is safe.
            replacement.replaceManifest(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
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
     * Loads the live manifest when present or calculates in-memory hashes without
     * creating live metadata.
     *
     * @return local digests used only for comparison
     * @throws IOException              if an existing manifest or GameData file cannot be read
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
     * Renews existing validated metadata without rewriting it, or safely publishes
     * a new manifest after local-file comparison succeeds.
     *
     * @param remoteManifest validated manifest to publish when none exists
     * @return non-fatal diagnostics from removing a committed temporary manifest
     * @throws ManifestRecoveryException if a failed publication cannot restore manifest absence
     * @throws InterruptedIOException    if interruption is observed before live metadata changes
     * @throws IOException               if the timestamp or new-manifest publication fails
     */
    private List<Throwable> renewManifest(Properties remoteManifest) throws IOException {
        throwIfInterruptedBeforeInstallation();
        if (Files.exists(manifest)) {
            Files.setLastModifiedTime(manifest, FileTime.from(Instant.now()));
            return List.of();
        }

        Path stagingDirectory = Files.createTempDirectory(dataDirectory, STAGING_PREFIX);
        Path stagedManifest = stagingDirectory.resolve(Globals.FILENAME_HASHES);
        Path attemptedManifest = backupPath(stagingDirectory, Globals.FILENAME_HASHES);
        List<Throwable> cleanupDiagnostics = new ArrayList<Throwable>();
        IOException operationFailure = null;
        boolean retainStagingDirectory = false;
        try {
            throwIfInterruptedBeforeInstallation();
            try (Writer writer = Files.newBufferedWriter(
                    stagedManifest,
                    StandardCharsets.UTF_8)) {
                remoteManifest.store(writer, "");
            }
            throwIfInterruptedBeforeInstallation();
            // Preserve the attempted commit bytes if manifest absence cannot be restored.
            Files.copy(
                    stagedManifest,
                    attemptedManifest,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
            throwIfInterruptedBeforeInstallation();
            publishManifest(stagedManifest, manifest);
        } catch (IOException cause) {
            operationFailure = cause;
            try {
                // Publication may have moved the file before reporting failure.
                restoreManifestAbsence(stagingDirectory);
            } catch (IOException recoveryCause) {
                retainStagingDirectory = true;
                Path recoveryDirectory = stagingDirectory.toAbsolutePath().normalize();
                List<Throwable> recoveryFailures = new ArrayList<Throwable>();
                recoveryFailures.add(new IOException(
                        "Unable to restore the missing GameData manifest at " + manifest + ".",
                        recoveryCause));
                recordRecoveryRequired(stagingDirectory, recoveryFailures);
                throw new ManifestRecoveryException(recoveryDirectory, cause, recoveryFailures);
            }
            throw cause;
        } finally {
            if (!retainStagingDirectory) {
                cleanupDiagnostics.addAll(deleteStagingDirectory(stagingDirectory));
                if (operationFailure != null) {
                    for (Throwable cleanupDiagnostic : cleanupDiagnostics) {
                        operationFailure.addSuppressed(cleanupDiagnostic);
                    }
                }
            }
        }
        return List.copyOf(cleanupDiagnostics);
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

    /**
     * Retains expected and observed digest evidence for one corrupt staged file.
     */
    private static final class DigestMismatchException extends Exception {

        /**
         * Creates a verification diagnostic for one module-selected filename.
         *
         * @param filename fixed GameData filename whose content was corrupt
         * @param expected digest required by the validated remote manifest
         * @param actual   digest calculated from the staged bytes
         */
        private DigestMismatchException(String filename, String expected, String actual) {
            super("GameData digest mismatch for " + filename + ": expected " + expected + ", received " + actual);
        }
    }

    /**
     * Combines the triggering installation fault with every rollback failure and
     * the exact directory retaining recovery material.
     */
    private static final class RecoveryException extends Exception {

        /**
         * Creates one caller-visible recovery diagnostic.
         *
         * @param recoveryDirectory exact retained recovery directory
         * @param installationCause failure that triggered rollback
         * @param recoveryFailures  failures encountered while restoring prior state
         */
        private RecoveryException(
                Path recoveryDirectory,
                Throwable installationCause,
                List<Throwable> recoveryFailures) {
            super("GameData recovery is incomplete; recovery files remain in " + recoveryDirectory + ".",
                    installationCause);
            recoveryFailures.forEach(this::addSuppressed);
        }
    }

    /**
     * Reports a first-manifest publication whose prior absence could not be
     * restored and identifies the private directory retaining attempted bytes.
     */
    private static final class ManifestRecoveryException extends IOException {

        private final Path recoveryDirectory;

        /**
         * Creates one retained-evidence diagnostic for incomplete manifest recovery.
         *
         * @param recoveryDirectory exact retained recovery directory
         * @param publicationCause  publication fault that triggered recovery
         * @param recoveryFailures  failures restoring absence or marking recovery
         */
        private ManifestRecoveryException(
                Path recoveryDirectory,
                Throwable publicationCause,
                List<Throwable> recoveryFailures) {
            super("GameData recovery is incomplete; recovery files remain in " + recoveryDirectory + ".",
                    publicationCause);
            this.recoveryDirectory = recoveryDirectory;
            recoveryFailures.forEach(this::addSuppressed);
        }

        /**
         * Returns the private directory retaining the attempted manifest bytes.
         *
         * @return exact recovery directory
         */
        private Path recoveryDirectory() {
            return recoveryDirectory;
        }
    }
}
