/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.artwork;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.ShipFaction;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Reads and installs the private, versioned Ship Artwork archive. */
final class ShipArtworkArchive {
    static final String FILENAME = "ship-artwork-v2.zip";
    static final String REPLACEMENT_FILENAME = "ship-artwork-v2.zip.new";
    private static final String BACKUP_FILENAME = "ship-artwork-v2.zip.previous";
    static final int SCHEMA_VERSION = 2;
    static final int RECIPE_VERSION = 1;
    private static final String MANIFEST = "manifest.properties";
    private static final String MANIFEST_DIGEST = "manifest.sha256";
    // Generated 64-pixel PNGs and metadata are far smaller; this ceiling bounds hostile inflation.
    private static final int MAX_REQUIRED_ENTRY_BYTES = 1024 * 1024;
    private static final System.Logger LOGGER = System.getLogger(ShipArtworkArchive.class.getName());

    private final Path archive;
    private final Path replacement;
    private final Path backup;
    private final FileMover fileMover;

    /** Supplies the narrow archive-move boundary used by installation and quarantine fault tests. */
    ShipArtworkArchive(Path dataDirectory, FileMover fileMover) {
        Path directory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        archive = directory.resolve(FILENAME);
        replacement = directory.resolve(REPLACEMENT_FILENAME);
        backup = directory.resolve(BACKUP_FILENAME);
        this.fileMover = Objects.requireNonNull(fileMover, "fileMover");
    }

    /**
     * Loads only an internally consistent v2 archive.
     *
     * @return decoded artwork, or an empty state when no archive exists
     * @throws IOException if archive structure, metadata, identity or integrity is invalid
     */
    State load() throws IOException {
        if (Files.notExists(archive)) {
            return State.empty();
        }
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Set<String> archivePaths = new HashSet<>();
            var zipEntries = zip.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry entry = zipEntries.nextElement();
                if (entry.isDirectory() || !archivePaths.add(entry.getName())) {
                    throw new IOException("Invalid or repeated Ship Artwork archive entry: " + entry.getName());
                }
            }
            byte[] manifest = requiredBytes(zip, MANIFEST);
            String recordedManifestDigest = new String(requiredBytes(zip, MANIFEST_DIGEST), StandardCharsets.US_ASCII)
                    .strip();
            if (!digest(manifest).equals(recordedManifestDigest)) {
                throw new IOException("Ship Artwork manifest digest does not match");
            }
            Map<String, String> properties = properties(manifest);
            requireVersion(properties, "schema.version", SCHEMA_VERSION);
            requireVersion(properties, "recipe.version", RECIPE_VERSION);

            SourceState sourceState = readSources(properties);
            Map<String, Instant> freshness = sourceState.freshness();
            int entryCount = count(properties, "entry.count");
            Map<Identity, BufferedImage> artwork = new HashMap<>();
            Set<String> paths = new HashSet<>();
            for (int index = 0; index < entryCount; index++) {
                String prefix = "entry." + index + ".";
                Identity identity = new Identity(
                        decode(required(properties, prefix + "source-image")),
                        enumValue(ShipFaction.class, required(properties, prefix + "faction")),
                        enumValue(Role.class, required(properties, prefix + "role")),
                        enumValue(Rarity.class, required(properties, prefix + "rarity")));
                String id = required(properties, prefix + "identity");
                if (!identity.archiveId().equals(id)) {
                    throw new IOException("Ship Artwork entry identity does not match its facts: " + id);
                }
                if (!freshness.containsKey(identity.sourceImage())) {
                    throw new IOException("Ship Artwork entry has no source freshness: " + id);
                }
                String path = required(properties, prefix + "path");
                if (!path.equals("artwork/" + id + ".png") || !paths.add(path)) {
                    throw new IOException("Invalid or repeated Ship Artwork entry path: " + path);
                }
                byte[] png = requiredBytes(zip, path);
                if (!digest(png).equals(required(properties, prefix + "sha256"))) {
                    throw new IOException("Ship Artwork image digest does not match: " + path);
                }
                BufferedImage image = ArtworkPng.decode(png, 64);
                if (image == null || image.getWidth() != 64 || image.getHeight() != 64) {
                    throw new IOException("Ship Artwork entry is not a 64-pixel PNG: " + path);
                }
                if (artwork.put(identity, image) != null) {
                    throw new IOException("Repeated Ship Artwork identity: " + id);
                }
            }
            // Earlier v2 archives have no legacy section and remain readable as current-only state.
            int legacyCount = properties.containsKey("legacy.count") ? count(properties, "legacy.count") : 0;
            Map<LegacyIdentity, BufferedImage> legacy = new HashMap<>();
            for (int index = 0; index < legacyCount; index++) {
                String prefix = "legacy." + index + ".";
                LegacyIdentity identity = new LegacyIdentity(
                        decode(required(properties, prefix + "ship-name")),
                        decode(required(properties, prefix + "source-image")),
                        enumValue(ShipFaction.class, required(properties, prefix + "faction")),
                        enumValue(Role.class, required(properties, prefix + "role")),
                        enumValue(Rarity.class, required(properties, prefix + "rarity")));
                String id = required(properties, prefix + "identity");
                if (!identity.archiveId().equals(id)) {
                    throw new IOException("Legacy Ship Artwork identity does not match its facts: " + id);
                }
                String path = required(properties, prefix + "path");
                if (!path.equals("legacy/" + id + ".png") || !paths.add(path)) {
                    throw new IOException("Invalid or repeated legacy Ship Artwork path: " + path);
                }
                byte[] png = requiredBytes(zip, path);
                if (!digest(png).equals(required(properties, prefix + "sha256"))) {
                    throw new IOException("Legacy Ship Artwork image digest does not match: " + path);
                }
                BufferedImage image = ArtworkPng.decode(png, 64);
                if (image == null || image.getWidth() != 64 || image.getHeight() != 64) {
                    throw new IOException("Legacy Ship Artwork entry is not a 64-pixel PNG: " + path);
                }
                if (legacy.put(identity, image) != null) {
                    throw new IOException("Repeated legacy Ship Artwork identity: " + id);
                }
            }
            paths.add(MANIFEST);
            paths.add(MANIFEST_DIGEST);
            if (!archivePaths.equals(paths)) {
                throw new IOException("Unexpected Ship Artwork archive entries");
            }
            return new State(artwork, legacy, freshness, sourceState.refreshDue());
        } catch (DateTimeParseException | IllegalArgumentException failure) {
            throw new IOException("Invalid Ship Artwork archive metadata", failure);
        }
    }

    /**
     * Moves unreadable state aside without replacing any earlier recovery evidence.
     *
     * @throws IOException if quarantine fails; the caller must disable persistence for this lifetime
     */
    void quarantine() throws IOException {
        Path recovery = archive.resolveSibling(FILENAME + ".corrupt-" + UUID.randomUUID());
        fileMover.move(archive, recovery);
        LOGGER.log(System.Logger.Level.WARNING, "Quarantined corrupt Ship Artwork at " + recovery);
    }

    /**
     * Writes a complete replacement archive before attempting live installation.
     *
     * @param state internally versioned artwork and per-source successful freshness
     * @throws IOException if encoding, writing or installation fails
     */
    void save(State state) throws IOException {
        Objects.requireNonNull(state, "state");
        Files.createDirectories(replacement.getParent());

        List<Map.Entry<Identity, BufferedImage>> entries = new ArrayList<>(state.artwork().entrySet());
        entries.sort(Map.Entry.comparingByKey(Comparator.comparing(Identity::archiveId)));
        List<Map.Entry<LegacyIdentity, BufferedImage>> legacyEntries = new ArrayList<>(state.legacy().entrySet());
        legacyEntries.sort(Map.Entry.comparingByKey(Comparator.comparing(LegacyIdentity::archiveId)));
        Set<String> usedSources = new HashSet<>();
        Map<String, byte[]> images = new HashMap<>();
        for (Map.Entry<Identity, BufferedImage> entry : entries) {
            usedSources.add(entry.getKey().sourceImage());
            images.put(entry.getKey().archiveId(), png(entry.getValue()));
        }
        for (Map.Entry<LegacyIdentity, BufferedImage> entry : legacyEntries) {
            images.put(entry.getKey().archiveId(), png(entry.getValue()));
        }
        List<String> sources = usedSources.stream().sorted().toList();
        for (String source : sources) {
            if (!state.freshness().containsKey(source)) {
                throw new IOException("Missing successful freshness for source image: " + source);
            }
        }

        byte[] manifest = manifest(entries, legacyEntries, sources, images,
                state.freshness(), state.refreshDue());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(replacement))) {
            write(zip, MANIFEST, manifest);
            write(zip, MANIFEST_DIGEST, (digest(manifest) + "\n").getBytes(StandardCharsets.US_ASCII));
            for (Map.Entry<Identity, BufferedImage> entry : entries) {
                String id = entry.getKey().archiveId();
                write(zip, "artwork/" + id + ".png", images.get(id));
            }
            for (Map.Entry<LegacyIdentity, BufferedImage> entry : legacyEntries) {
                String id = entry.getKey().archiveId();
                write(zip, "legacy/" + id + ".png", images.get(id));
            }
        }

        try {
            fileMover.move(replacement, archive,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException | FileAlreadyExistsException unsupported) {
            installWithRollback();
        }
    }

    /**
     * Installs the completed replacement non-atomically while retaining a restorable prior archive.
     * If restoration itself fails, the completed prior bytes remain at the private backup path.
     */
    private void installWithRollback() throws IOException {
        if (Files.notExists(archive)) {
            fileMover.move(replacement, archive, StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        // Finish the backup before allowing a non-atomic provider to touch the live archive.
        Files.copy(archive, backup,
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        try {
            fileMover.move(replacement, archive, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException installationFailure) {
            try {
                Files.copy(backup, archive,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (IOException restorationFailure) {
                // Retain the private backup when even restoring known-good bytes is unavailable.
                installationFailure.addSuppressed(restorationFailure);
                throw installationFailure;
            }
            try {
                Files.deleteIfExists(backup);
            } catch (IOException cleanupFailure) {
                // A redundant backup is safer than obscuring the original installation failure.
                installationFailure.addSuppressed(cleanupFailure);
            }
            throw installationFailure;
        }
        try {
            Files.deleteIfExists(backup);
        } catch (IOException cleanupFailure) {
            // The newly installed archive is valid; leftover rollback evidence is nonfatal.
            LOGGER.log(System.Logger.Level.WARNING,
                    "Cannot remove prior Ship Artwork archive backup: " + backup, cleanupFailure);
        }
    }

    /** Builds deterministic line-oriented metadata for digesting and operator inspection. */
    private static byte[] manifest(List<Map.Entry<Identity, BufferedImage>> entries,
                                   List<Map.Entry<LegacyIdentity, BufferedImage>> legacyEntries,
                                   List<String> sources, Map<String, byte[]> images,
                                   Map<String, Instant> freshness, Set<String> refreshDue) {
        StringBuilder result = new StringBuilder();
        property(result, "schema.version", Integer.toString(SCHEMA_VERSION));
        property(result, "recipe.version", Integer.toString(RECIPE_VERSION));
        property(result, "source.count", Integer.toString(sources.size()));
        for (int index = 0; index < sources.size(); index++) {
            String source = sources.get(index);
            property(result, "source." + index + ".identity", encode(source));
            property(result, "source." + index + ".succeeded-at", freshness.get(source).toString());
            property(result, "source." + index + ".refresh-due",
                    Boolean.toString(refreshDue.contains(source)));
        }
        property(result, "entry.count", Integer.toString(entries.size()));
        for (int index = 0; index < entries.size(); index++) {
            Identity identity = entries.get(index).getKey();
            String id = identity.archiveId();
            String prefix = "entry." + index + ".";
            property(result, prefix + "identity", id);
            property(result, prefix + "source-image", encode(identity.sourceImage()));
            property(result, prefix + "faction", identity.faction().name());
            property(result, prefix + "role", identity.role().name());
            property(result, prefix + "rarity", identity.rarity().name());
            property(result, prefix + "path", "artwork/" + id + ".png");
            property(result, prefix + "sha256", digest(images.get(id)));
        }
        property(result, "legacy.count", Integer.toString(legacyEntries.size()));
        for (int index = 0; index < legacyEntries.size(); index++) {
            LegacyIdentity identity = legacyEntries.get(index).getKey();
            String id = identity.archiveId();
            String prefix = "legacy." + index + ".";
            property(result, prefix + "identity", id);
            property(result, prefix + "ship-name", encode(identity.shipName()));
            property(result, prefix + "source-image", encode(identity.sourceImage()));
            property(result, prefix + "faction", identity.faction().name());
            property(result, prefix + "role", identity.role().name());
            property(result, prefix + "rarity", identity.rarity().name());
            property(result, prefix + "path", "legacy/" + id + ".png");
            property(result, prefix + "sha256", digest(images.get(id)));
        }
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Parses unique properties without accepting continuation or escape ambiguity. */
    private static Map<String, String> properties(byte[] bytes) throws IOException {
        Map<String, String> result = new HashMap<>();
        for (String line : new String(bytes, StandardCharsets.UTF_8).split("\\R")) {
            if (line.isEmpty()) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0 || result.put(line.substring(0, separator), line.substring(separator + 1)) != null) {
                throw new IOException("Invalid or repeated Ship Artwork manifest property");
            }
        }
        return result;
    }

    /** Reads successful timestamps and launch-time eligibility by remote source identity. */
    private static SourceState readSources(Map<String, String> properties) throws IOException {
        int sourceCount = count(properties, "source.count");
        Map<String, Instant> freshness = new HashMap<>();
        Set<String> refreshDue = new HashSet<>();
        for (int index = 0; index < sourceCount; index++) {
            String prefix = "source." + index + ".";
            String source = decode(required(properties, prefix + "identity"));
            Instant succeededAt = Instant.parse(required(properties, prefix + "succeeded-at"));
            if (freshness.put(source, succeededAt) != null) {
                throw new IOException("Repeated Ship Artwork source image: " + source);
            }
            String due = required(properties, prefix + "refresh-due");
            if (!due.equals("true") && !due.equals("false")) {
                throw new IOException("Invalid Ship Artwork refresh state: " + source);
            }
            if (Boolean.parseBoolean(due)) {
                refreshDue.add(source);
            }
        }
        return new SourceState(freshness, refreshDue);
    }

    /** Returns a required zip entry's bytes without allowing unbounded decompression. */
    private static byte[] requiredBytes(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null || entry.isDirectory()) {
            throw new IOException("Missing Ship Artwork archive entry: " + name);
        }
        try (var input = zip.getInputStream(entry)) {
            byte[] bytes = input.readNBytes(MAX_REQUIRED_ENTRY_BYTES + 1);
            if (bytes.length > MAX_REQUIRED_ENTRY_BYTES) {
                throw new IOException("Oversized Ship Artwork archive entry: " + name);
            }
            return bytes;
        }
    }

    /** Writes one deterministic file entry to a replacement zip. */
    private static void write(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        try {
            zip.write(bytes);
        } finally {
            zip.closeEntry();
        }
    }

    /** Encodes one composed image as PNG before installation begins. */
    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IOException("No PNG writer is available for Ship Artwork");
        }
        return output.toByteArray();
    }

    /** Computes lowercase SHA-256 integrity evidence. */
    private static String digest(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void property(StringBuilder result, String key, String value) {
        result.append(key).append('=').append(value).append('\n');
    }

    /** Parses one required non-negative manifest count. */
    private static int count(Map<String, String> properties, String key) throws IOException {
        try {
            int value = Integer.parseInt(required(properties, key));
            if (value < 0) {
                throw new NumberFormatException("negative");
            }
            return value;
        } catch (NumberFormatException failure) {
            throw new IOException("Invalid Ship Artwork count: " + key, failure);
        }
    }

    /** Rejects a manifest whose required version differs from this implementation. */
    private static void requireVersion(Map<String, String> properties, String key, int expected) throws IOException {
        if (!Integer.toString(expected).equals(required(properties, key))) {
            throw new IOException("Unsupported Ship Artwork " + key);
        }
    }

    /** Returns one required non-empty manifest value. */
    private static String required(Map<String, String> properties, String key) throws IOException {
        String value = properties.get(key);
        if (value == null || value.isEmpty()) {
            throw new IOException("Missing Ship Artwork manifest property: " + key);
        }
        return value;
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        return Enum.valueOf(type, value);
    }

    /** Identity of the facts that produce one composed specific Ship image. */
    record Identity(String sourceImage, ShipFaction faction, Role role, Rarity rarity) {
        Identity {
            Objects.requireNonNull(sourceImage, "sourceImage");
            Objects.requireNonNull(faction, "faction");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(rarity, "rarity");
        }

        /** Creates identity only from canonical facts consumed by the composition recipe. */
        static Identity from(Ship ship) {
            return new Identity(ship.getIconName(), ship.getFaction(), ship.getRole(), ship.getRarity());
        }

        /** Includes the recipe version in the opaque archive identity. */
        String archiveId() {
            String canonical = RECIPE_VERSION + "\0" + sourceImage + "\0" + faction.name()
                    + "\0" + role.name() + "\0" + rarity.name();
            return digest(canonical.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Legacy composed pixels are keyed separately from the current composition recipe. */
    record LegacyIdentity(String shipName, String sourceImage,
                          ShipFaction faction, Role role, Rarity rarity) {
        LegacyIdentity {
            Objects.requireNonNull(shipName, "shipName");
            Objects.requireNonNull(sourceImage, "sourceImage");
            Objects.requireNonNull(faction, "faction");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(rarity, "rarity");
        }

        /** Binds stale pixels to the exact canonical Ship facts seen during migration. */
        static LegacyIdentity from(Ship ship) {
            return new LegacyIdentity(ship.getName(), ship.getIconName(),
                    ship.getFaction(), ship.getRole(), ship.getRarity());
        }

        /** Gives legacy provenance its own stable namespace, never a current recipe identity. */
        String archiveId() {
            String canonical = "legacy\0" + shipName + "\0" + sourceImage + "\0" + faction.name()
                    + "\0" + role.name() + "\0" + rarity.name();
            return digest(canonical.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Immutable relationship between current entries, stale legacy pixels and source freshness. */
    record State(Map<Identity, BufferedImage> artwork, Map<LegacyIdentity, BufferedImage> legacy,
                 Map<String, Instant> freshness,
                 Set<String> refreshDue) {
        State {
            artwork = Map.copyOf(artwork);
            legacy = Map.copyOf(legacy);
            freshness = Map.copyOf(freshness);
            refreshDue = Set.copyOf(refreshDue);
        }

        static State empty() {
            return new State(Map.of(), Map.of(), Map.of(), Set.of());
        }
    }

    /** Per-source timestamps and launch-time refresh eligibility. */
    private record SourceState(Map<String, Instant> freshness, Set<String> refreshDue) { }

    /** Moves an archive using filesystem-provider semantics for installation or quarantine. */
    @FunctionalInterface
    interface FileMover {
        /**
         * Moves one archive with the requested provider options, without replacement for quarantine.
         *
         * @return destination path
         * @throws IOException if the move fails
         */
        Path move(Path source, Path target, CopyOption... options) throws IOException;
    }
}
