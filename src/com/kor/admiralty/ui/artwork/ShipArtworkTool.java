/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.artwork;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.io.GameDataLoadException;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Headless, explicitly targeted maintenance operations for persisted Ship Artwork. */
public final class ShipArtworkTool {
    private static final String USAGE = "Usage: inspect|migrate|verify|cleanup --data-directory <directory> "
            + "[--json] [--online-refresh for migrate] [--confirm-legacy-cleanup for cleanup]";
    private static final int INVALID_ARGUMENTS = 2;
    private static final int FINDINGS = 3;
    private static final int OPERATIONAL_FAILURE = 4;

    private ShipArtworkTool() { }

    /**
     * Runs one command and exits with its report category for shell callers.
     * The data directory must be supplied explicitly; no application bootstrap is used.
     */
    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * Executes one command without exiting the JVM so tests can inspect the exact status.
     * Invalid arguments return 2, findings return 3, and operational failures return 4.
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
        return run(args, out, err,
                path -> Files.readAttributes(path, BasicFileAttributes.class).isDirectory());
    }

    /**
     * Supplies an internal directory probe for deterministic filesystem-failure tests.
     * @return the command's exit category without exiting the JVM
     */
    static int run(String[] args, PrintStream out, PrintStream err, DirectoryProbe directoryProbe) {
        return run(args, out, err, directoryProbe,
                (directory, gameData) -> ShipArtwork.open(directory, gameData, List.of()));
    }

    /**
     * Supplies the production online opener's test seam without changing command reporting.
     * @return the command's exit category without exiting the JVM
     */
    static int run(String[] args, PrintStream out, PrintStream err, DirectoryProbe directoryProbe,
                   OnlineArtworkOpener onlineOpener) {
        boolean json = args != null && List.of(args).contains("--json");
        Report report;
        try {
            Options options = parse(args, directoryProbe);
            json = options.json();
            report = execute(options, onlineOpener);
        } catch (IllegalArgumentException failure) {
            report = new Report("invalid", null);
            report.error = failure.getMessage() + ". " + USAGE;
            report.exitCode = INVALID_ARGUMENTS;
        } catch (IOException | SecurityException failure) {
            report = new Report("failed", null);
            report.error = explanation(failure);
            report.exitCode = OPERATIONAL_FAILURE;
        }
        PrintStream destination = json || report.exitCode <= FINDINGS ? out : err;
        destination.println(json ? report.json() : report.human());
        return report.exitCode;
    }

    /**
     * Parses only explicit commands and target directories, rejecting repeated or unknown options.
     * @throws IOException if the selected directory's attributes or real path cannot be read
     * @throws IllegalArgumentException if a command, option, or directory value is invalid
     * @throws SecurityException if filesystem access is denied by the runtime
     */
    private static Options parse(String[] args, DirectoryProbe directoryProbe) throws IOException {
        if (args == null || args.length == 0) throw new IllegalArgumentException("Missing operation");
        String operation = args[0];
        if (!Set.of("inspect", "migrate", "verify", "cleanup").contains(operation)) {
            throw new IllegalArgumentException("Unknown operation: " + operation);
        }
        String directory = null;
        boolean json = false;
        boolean confirmed = false;
        boolean onlineRefresh = false;
        for (int index = 1; index < args.length; index++) {
            switch (args[index]) {
                case "--data-directory" -> {
                    if (directory != null || ++index == args.length || args[index].isBlank()) {
                        throw new IllegalArgumentException("Expected one --data-directory value");
                    }
                    directory = args[index];
                }
                case "--json" -> {
                    if (json) throw new IllegalArgumentException("Repeated --json option");
                    json = true;
                }
                case "--confirm-legacy-cleanup" -> {
                    if (confirmed || !operation.equals("cleanup")) {
                        throw new IllegalArgumentException("Unexpected --confirm-legacy-cleanup option");
                    }
                    confirmed = true;
                }
                case "--online-refresh" -> {
                    if (onlineRefresh || !operation.equals("migrate")) {
                        throw new IllegalArgumentException("Unexpected --online-refresh option");
                    }
                    onlineRefresh = true;
                }
                default -> throw new IllegalArgumentException("Unknown option: " + args[index]);
            }
        }
        if (directory == null) throw new IllegalArgumentException("Missing --data-directory option");
        Path target = Path.of(directory).toAbsolutePath().normalize();
        boolean isDirectory;
        try {
            isDirectory = directoryProbe.isDirectory(target);
        } catch (NoSuchFileException absent) {
            throw new IllegalArgumentException("Data directory does not exist: " + target);
        }
        if (!isDirectory) throw new IllegalArgumentException("Data directory is not a directory: " + target);
        return new Options(operation, target.toRealPath(), json, confirmed, onlineRefresh);
    }

    /** Dispatches after target validation, without resolving any application default directory. */
    private static Report execute(Options options, OnlineArtworkOpener onlineOpener) {
        Report report = new Report(options.operation(), options.directory());
        if (options.operation().equals("cleanup")) {
            report.cleanupStatus = "refused";
            report.cleanupTarget = options.directory().resolve("icons.zip");
        }
        try {
            ShipArtworkArchive archive = new ShipArtworkArchive(options.directory(), Files::move);
            Path v2 = options.directory().resolve(ShipArtworkArchive.FILENAME);
            boolean present = Files.exists(v2);
            if (!present && !Files.notExists(v2)) {
                throw new IOException("Cannot determine whether the v2 archive exists: " + v2);
            }
            ShipArtworkArchive.State state = ShipArtworkArchive.State.empty();
            if (present) {
                try {
                    state = archive.load();
                    report.archiveStatus = "valid";
                    report.count(state);
                } catch (ShipArtworkArchive.InvalidArchiveException invalid) {
                    report.archiveStatus = "invalid";
                    report.findings.add(new Finding("INVALID_V2", explanation(invalid)));
                } catch (IOException failure) {
                    report.error = "Cannot read v2 archive: " + explanation(failure);
                }
            }

            if (report.error == null) {
                switch (options.operation()) {
                    case "inspect" -> inspect(options.directory(), state, report);
                    case "migrate" -> migrate(options.directory(), archive, state, report,
                            options.onlineRefresh(), onlineOpener);
                    case "verify" -> {
                        if (!present) report.findings.add(new Finding("MISSING_V2", "No v2 archive exists"));
                    }
                    case "cleanup" -> cleanup(options, present, report);
                    default -> throw new IllegalStateException("Validated operation was lost");
                }
            }
        } catch (IOException | GameDataLoadException | RuntimeException failure) {
            report.error = explanation(failure);
        } catch (OfflineAcquisitionAttempt failure) {
            report.error = failure.getMessage();
        }
        report.finish();
        return report;
    }

    /**
     * Deletes only the direct legacy file after v2 was verified in this command invocation.
     * Linked or special archive paths cannot stand in for state inside the resolved directory.
     * @throws IOException if archive attributes or the exact deletion fail
     */
    private static void cleanup(Options options, boolean v2Present, Report report) throws IOException {
        if (!options.confirmed()) {
            report.findings.add(new Finding("CONFIRMATION_REQUIRED",
                    "Cleanup requires --confirm-legacy-cleanup"));
        }
        if (!v2Present) {
            report.findings.add(new Finding("MISSING_V2", "No v2 archive exists"));
        }
        if (!options.confirmed() || !report.archiveStatus.equals("valid")) return;

        Path v2 = options.directory().resolve(ShipArtworkArchive.FILENAME);
        if (!Files.readAttributes(v2, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isRegularFile()) {
            throw new IOException("V2 archive is not a direct regular file: " + v2);
        }
        BasicFileAttributes legacyAttributes;
        try {
            legacyAttributes = Files.readAttributes(report.cleanupTarget, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException absent) {
            report.findings.add(new Finding("MISSING_LEGACY", "No legacy archive exists"));
            return;
        }
        if (!legacyAttributes.isRegularFile()) {
            throw new IOException("Legacy archive is not a direct regular file: " + report.cleanupTarget);
        }
        Files.delete(report.cleanupTarget);
        report.cleanupStatus = "deleted";
    }

    /**
     * Inventories legacy decisions and v2 state without constructing the mutable artwork lifetime.
     * @throws GameDataLoadException if legacy mapping needs unavailable local reference data
     */
    private static void inspect(Path directory, ShipArtworkArchive.State state, Report report)
            throws GameDataLoadException {
        Path legacy = directory.resolve("icons.zip");
        if (Files.notExists(legacy)) {
            report.legacy = LegacyArtworkMigration.Outcome.empty();
            return;
        }
        GameData gameData = GameData.load(directory);
        report.legacy = LegacyArtworkMigration.migrate(
                legacy, gameData, state.artwork().keySet(), state.legacy().keySet()).outcome();
        report.legacyFindings();
    }

    /**
     * Runs the application's lazy migration and optionally waits for forced GitHub refresh.
     * Offline migration fails loudly if acquisition is attempted; both paths use the same
     * canonical artwork lifetime and the explicitly selected directory.
     * @throws GameDataLoadException if local canonical Ships cannot be loaded
     * @throws IOException if online refresh is interrupted or v2 installation/validation fails
     */
    private static void migrate(Path directory, ShipArtworkArchive archive,
                                ShipArtworkArchive.State prior, Report report,
                                boolean onlineRefresh, OnlineArtworkOpener onlineOpener)
            throws GameDataLoadException, IOException {
        if (report.archiveStatus.equals("invalid")) return;
        GameData gameData = GameData.load(directory);
        ShipArtwork artwork = onlineRefresh ? onlineOpener.open(directory, gameData)
                : new ShipArtwork(directory, gameData, List.of(),
                        ShipArtworkTool::resource, ShipArtworkTool::refuseAcquisition);
        try (artwork) {
            report.legacy = artwork.migrationOutcome();
            if (onlineRefresh) {
                try {
                    report.onlineRefresh = artwork.refreshOnlineAndAwait(gameData.ships());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Online refresh interrupted", interrupted);
                }
            }
        }
        if (artwork.persistenceFailure() != null) {
            throw new IOException("Cannot install migrated v2 archive", artwork.persistenceFailure());
        }
        report.legacyFindings();
        if (report.onlineRefresh != null && report.onlineRefresh.failed() > 0) {
            report.findings.add(new Finding("ONLINE_REFRESH_FAILED",
                    report.onlineRefresh.failed() + " remote source(s) did not refresh"));
        }
        if (report.legacy != null && report.legacy.archiveError() != null) return;
        Path v2 = directory.resolve(ShipArtworkArchive.FILENAME);
        if (Files.exists(v2)) {
            ShipArtworkArchive.State installed = archive.load();
            report.archiveStatus = "valid";
            report.count(installed);
            // A successful close must make every newly admitted stale image restart-visible.
            Map<String, Ship> canonical = LegacyArtworkMigration.uniqueShips(gameData);
            for (LegacyArtworkMigration.Detail detail : report.legacy.details()) {
                if (detail.reason() == LegacyArtworkMigration.Reason.MIGRATED) {
                    Ship ship = canonical.get(detail.filename());
                    if (ship == null || !installed.legacy().containsKey(ShipArtworkArchive.LegacyIdentity.from(ship))) {
                        throw new IOException("Migrated artwork was not installed: " + detail.filename());
                    }
                }
            }
            if (report.onlineRefresh != null) {
                for (Map.Entry<String, java.time.Instant> success : report.onlineRefresh.succeededAt().entrySet()) {
                    if (!success.getValue().equals(installed.freshness().get(success.getKey()))) {
                        throw new IOException("Refreshed artwork was not installed: " + success.getKey());
                    }
                }
            }
            report.migrationInstalled = true;
        } else if (report.legacy.migrated() > 0 || !prior.legacy().isEmpty()
                || report.onlineRefresh != null && report.onlineRefresh.succeeded() > 0) {
            throw new IOException("Migrated v2 archive is missing after close");
        }
    }

    /** Loads only packaged assets; the operator never obtains an HTTP client. */
    private static InputStream resource(String name) {
        return ShipArtworkTool.class.getResourceAsStream("/com/kor/admiralty/ui/resources/" + name);
    }

    /**
     * Escapes the artwork lifetime's ordinary transport-failure handling so an accidental
     * network request becomes an operational failure, never an apparently offline success.
     */
    static void refuseAcquisition(String name, Consumer<BufferedImage> completed) {
        throw new OfflineAcquisitionAttempt(name);
    }

    /** Returns a useful failure message even when an I/O exception contains only a cause. */
    private static String explanation(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
        return failure.getCause() == null ? message : message + ": " + explanation(failure.getCause());
    }

    private record Options(String operation, Path directory, boolean json,
                           boolean confirmed, boolean onlineRefresh) { }

    /** Reads directory type without hiding an underlying filesystem failure. */
    @FunctionalInterface
    interface DirectoryProbe {
        /**
         * Returns whether the path is a directory.
         * @throws IOException if the path's attributes cannot be read
         */
        boolean isDirectory(Path path) throws IOException;
    }

    /** Opens the production artwork lifetime, substitutable with scripted HTTP in tests. */
    @FunctionalInterface
    interface OnlineArtworkOpener {
        /** Returns an artwork lifetime with production source validation and composition. */
        ShipArtwork open(Path directory, GameData gameData);
    }

    private record Finding(String code, String message) { }

    /** Error is intentional: ShipArtwork turns RuntimeException from an adapter into a retry. */
    private static final class OfflineAcquisitionAttempt extends AssertionError {
        private OfflineAcquisitionAttempt(String name) {
            super("Offline migration attempted network acquisition for " + name);
        }
    }

    /** One structured outcome is rendered for both people and automation. */
    private static final class Report {
        private final String operation;
        private final Path directory;
        private String archiveStatus = "absent";
        private int currentEntries;
        private int staleEntries;
        private int sources;
        private LegacyArtworkMigration.Outcome legacy;
        private boolean migrationInstalled;
        private ShipArtwork.RefreshOutcome onlineRefresh;
        private String cleanupStatus;
        private Path cleanupTarget;
        private final List<Finding> findings = new ArrayList<>();
        private String error;
        private int exitCode;

        private Report(String operation, Path directory) {
            this.operation = operation;
            this.directory = directory;
        }

        /** Copies the fully validated archive's inventory into the report. */
        private void count(ShipArtworkArchive.State state) {
            currentEntries = state.artwork().size();
            staleEntries = state.legacy().size();
            sources = state.freshness().size();
        }

        /** Converts unmatched and unreadable legacy decisions into script-visible findings. */
        private void legacyFindings() {
            if (legacy == null) return;
            if (legacy.archiveError() != null) {
                error = "Cannot inspect legacy archive: " + legacy.archiveError();
                return;
            }
            if (legacy.unreadable() > 0) {
                findings.add(new Finding("UNREADABLE_LEGACY", legacy.unreadable() + " unreadable legacy entries"));
            }
            if (legacy.unmatched() > 0) {
                findings.add(new Finding("UNMATCHED_LEGACY", legacy.unmatched() + " unmatched legacy entries"));
            }
        }

        /** Assigns precedence to operational failure, then findings, then a clean outcome. */
        private void finish() {
            exitCode = error != null ? OPERATIONAL_FAILURE : findings.isEmpty() ? 0 : FINDINGS;
        }

        /** Distinguishes a read-only candidate from an image confirmed in the installed v2 archive. */
        private int migratedCount() {
            return legacy != null && migrationInstalled ? legacy.migrated() : 0;
        }

        /** Keeps the candidate decision shared by both report projections. */
        private boolean wouldMigrate(LegacyArtworkMigration.Detail detail) {
            return detail.reason() == LegacyArtworkMigration.Reason.MIGRATED && !migrationInstalled;
        }

        /** Names an inspect candidate without claiming that read-only inspection changed the archive. */
        private String reason(LegacyArtworkMigration.Detail detail) {
            return wouldMigrate(detail) ? "WOULD_MIGRATE" : detail.reason().name();
        }

        /** Explains a potential migration consistently in human and JSON reports. */
        private String explanation(LegacyArtworkMigration.Detail detail) {
            return wouldMigrate(detail) ? "Eligible for stale migration" : detail.explanation();
        }

        /** Projects the structured report into concise, interactive text. */
        private String human() {
            StringBuilder text = new StringBuilder();
            text.append("Operation: ").append(operation).append('\n');
            if (directory != null) text.append("Data directory: ").append(directory).append('\n');
            text.append("V2 archive: ").append(archiveStatus).append(" (current=")
                    .append(currentEntries).append(", stale=").append(staleEntries)
                    .append(", sources=").append(sources).append(")\n");
            if (legacy != null) {
                text.append("Legacy archive: ").append(legacy.archivePresent() ? "present" : "absent")
                        .append(" (matched=").append(legacy.matched()).append(", eligible=")
                        .append(legacy.migrated()).append(", migrated=").append(migratedCount())
                        .append(", unreadable=").append(legacy.unreadable())
                        .append(", unmatched=").append(legacy.unmatched()).append(")\n");
                for (LegacyArtworkMigration.Detail detail : legacy.details()) {
                    text.append("  ").append(detail.filename()).append(": ")
                            .append(reason(detail)).append(" - ").append(explanation(detail)).append('\n');
                }
            }
            if (onlineRefresh != null) {
                text.append("Online refresh: requested=").append(onlineRefresh.requested())
                        .append(", succeeded=").append(onlineRefresh.succeeded())
                        .append(", failed=").append(onlineRefresh.failed()).append('\n');
            }
            if (cleanupStatus != null) {
                text.append("Legacy cleanup: ").append(cleanupStatus)
                        .append(" (").append(cleanupTarget).append(")\n");
            }
            for (Finding finding : findings) {
                text.append("Finding [").append(finding.code()).append("]: ")
                        .append(finding.message()).append('\n');
            }
            if (error != null) text.append("Error: ").append(error).append('\n');
            text.append("Result: ").append(status()).append(" (exit ").append(exitCode).append(')');
            return text.toString();
        }

        /** Projects the same report into a stable JSON object without a JSON dependency. */
        private String json() {
            StringBuilder text = new StringBuilder();
            text.append('{');
            text.append("\"operation\":").append(quoted(operation));
            text.append(",\"dataDirectory\":").append(quoted(directory == null ? null : directory.toString()));
            text.append(",\"status\":").append(quoted(status()));
            text.append(",\"exitCode\":").append(exitCode);
            text.append(",\"v2\":{");
            text.append("\"status\":").append(quoted(archiveStatus));
            text.append(",\"currentEntries\":").append(currentEntries);
            text.append(",\"staleEntries\":").append(staleEntries);
            text.append(",\"sources\":").append(sources).append('}');
            text.append(",\"legacy\":");
            if (legacy == null) {
                text.append("null");
            } else {
                text.append('{');
                text.append("\"present\":").append(legacy.archivePresent());
                text.append(",\"matched\":").append(legacy.matched());
                text.append(",\"eligible\":").append(legacy.migrated());
                text.append(",\"migrated\":").append(migratedCount());
                text.append(",\"unreadable\":").append(legacy.unreadable());
                text.append(",\"unmatched\":").append(legacy.unmatched());
                text.append(",\"archiveError\":").append(quoted(legacy.archiveError()));
                text.append(",\"details\":[");
                for (int index = 0; index < legacy.details().size(); index++) {
                    if (index > 0) text.append(',');
                    LegacyArtworkMigration.Detail detail = legacy.details().get(index);
                    text.append('{');
                    text.append("\"filename\":").append(quoted(detail.filename()));
                    text.append(",\"shipName\":").append(quoted(detail.shipName()));
                    text.append(",\"disposition\":").append(quoted(detail.disposition().name()));
                    text.append(",\"reason\":").append(quoted(reason(detail)));
                    text.append(",\"explanation\":").append(quoted(explanation(detail)));
                    text.append('}');
                }
                text.append("]}");
            }
            text.append(",\"onlineRefresh\":");
            if (onlineRefresh == null) {
                text.append("null");
            } else {
                text.append("{\"requested\":").append(onlineRefresh.requested())
                        .append(",\"succeeded\":").append(onlineRefresh.succeeded())
                        .append(",\"failed\":").append(onlineRefresh.failed()).append('}');
            }
            text.append(",\"cleanup\":");
            if (cleanupStatus == null) {
                text.append("null");
            } else {
                text.append("{\"status\":").append(quoted(cleanupStatus))
                        .append(",\"target\":").append(quoted(cleanupTarget.toString())).append('}');
            }
            text.append(",\"findings\":[");
            for (int index = 0; index < findings.size(); index++) {
                if (index > 0) text.append(',');
                Finding finding = findings.get(index);
                text.append("{\"code\":").append(quoted(finding.code()))
                        .append(",\"message\":").append(quoted(finding.message())).append('}');
            }
            text.append(']');
            text.append(",\"error\":").append(quoted(error));
            return text.append('}').toString();
        }

        /** Names the category represented by the numeric exit status. */
        private String status() {
            return switch (exitCode) {
                case INVALID_ARGUMENTS -> "invalid-arguments";
                case FINDINGS -> "findings";
                case OPERATIONAL_FAILURE -> "operational-failure";
                default -> "ok";
            };
        }

        /** Escapes arbitrary filenames and diagnostics as JSON string values. */
        private static String quoted(String value) {
            if (value == null) return "null";
            StringBuilder text = new StringBuilder("\"");
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                switch (character) {
                    case '\"' -> text.append("\\\"");
                    case '\\' -> text.append("\\\\");
                    case '\b' -> text.append("\\b");
                    case '\f' -> text.append("\\f");
                    case '\n' -> text.append("\\n");
                    case '\r' -> text.append("\\r");
                    case '\t' -> text.append("\\t");
                    default -> {
                        if (character < 0x20) {
                            text.append("\\u").append(String.format("%04x", (int) character));
                        } else {
                            text.append(character);
                        }
                    }
                }
            }
            return text.append('\"').toString();
        }
    }
}
