/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.artwork;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.io.GameData;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Reads old composed artwork without giving its pixels current recipe or freshness provenance. */
final class LegacyArtworkMigration {
    // Legacy files predate integrity metadata; cap inflated bytes before decoding user state.
    private static final int MAX_ENTRY_BYTES = 1024 * 1024;

    private LegacyArtworkMigration() { }

    /** Why one legacy ZIP entry was carried forward or omitted. */
    enum Reason { MIGRATED, ALREADY_MIGRATED, ALREADY_CURRENT, NO_CANONICAL_SHIP,
        AMBIGUOUS_FILENAME, UNREADABLE_IMAGE, DIRECTORY_ENTRY, DUPLICATE_ENTRY }

    /** The outcome category of one legacy ZIP entry. */
    enum Disposition { MATCHED, UNREADABLE, UNMATCHED }

    /** One inspectable entry decision, including an omission explanation when applicable. */
    record Detail(String filename, String shipName, Disposition disposition, Reason reason,
                  String explanation) { }

    /**
     * Counts and decisions from one read-only scan. Migrated means admitted to this lifetime's
     * stale fallback; v2 persistence can complete later or fail independently.
     */
    record Outcome(boolean archivePresent, int matched, int migrated, int unreadable, int unmatched,
                   List<Detail> details, String archiveError) {
        Outcome {
            details = List.copyOf(details);
        }

        /** Reports that this lifetime has no legacy archive to inspect. */
        static Outcome empty() {
            return new Outcome(false, 0, 0, 0, 0, List.of(), null);
        }
    }

    /** Newly reusable stale pixels and the complete decision report from one legacy scan. */
    record Result(Map<ShipArtworkArchive.LegacyIdentity, BufferedImage> artwork, Outcome outcome) {
        Result {
            artwork = Map.copyOf(artwork);
        }
    }

    /** Exact canonical filename matches and filenames shared by multiple Ships. */
    private record ShipIndex(Map<String, Ship> unique, Set<String> ambiguous) { }

    /**
     * Reads legacy entries without changing their archive, reporting every inspected decision.
     * Only valid pixels for a unique canonical Ship absent from current and prior stale state
     * are returned for v2 persistence; no remote source is contacted.
     */
    static Result migrate(Path archive, GameData gameData,
                          Set<ShipArtworkArchive.Identity> current,
                          Set<ShipArtworkArchive.LegacyIdentity> existing) {
        if (Files.notExists(archive)) return new Result(Map.of(), Outcome.empty());
        ShipIndex index = index(gameData);
        Map<ShipArtworkArchive.LegacyIdentity, BufferedImage> migrated = new HashMap<>();
        List<Detail> details = new ArrayList<>();
        int matched = 0;
        int unreadable = 0;
        int unmatched = 0;
        String archiveError = null;
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            List<ZipEntry> entries = new ArrayList<>();
            Map<String, Integer> occurrences = new HashMap<>();
            var enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                entries.add(entry);
                occurrences.merge(entry.getName(), 1, Integer::sum);
            }
            for (ZipEntry entry : entries) {
                String filename = entry.getName();
                if (entry.isDirectory()) {
                    unmatched++;
                    details.add(new Detail(filename, null, Disposition.UNMATCHED,
                            Reason.DIRECTORY_ENTRY, "Directory has no composed pixels"));
                    continue;
                }
                if (occurrences.get(filename) > 1) {
                    unmatched++;
                    details.add(new Detail(filename, null, Disposition.UNMATCHED,
                            Reason.DUPLICATE_ENTRY, "Repeated filename cannot identify one image"));
                    continue;
                }
                Ship ship = index.unique().get(filename);
                BufferedImage image;
                try (var input = zip.getInputStream(entry)) {
                    byte[] png = input.readNBytes(MAX_ENTRY_BYTES + 1);
                    if (png.length > MAX_ENTRY_BYTES) {
                        throw new IOException("Legacy image exceeds the one MiB entry limit");
                    }
                    image = ArtworkPng.decode(png, 64);
                    if (image.getWidth() != 64 || image.getHeight() != 64) {
                        throw new IOException("Legacy artwork is not 64 by 64 pixels");
                    }
                } catch (IOException failure) {
                    // A corrupt entry cannot invalidate an earlier match or the untouched ZIP.
                    unreadable++;
                    details.add(new Detail(filename, ship == null ? null : ship.getName(), Disposition.UNREADABLE,
                            Reason.UNREADABLE_IMAGE, explanation(failure)));
                    continue;
                }

                if (ship == null) {
                    unmatched++;
                    Reason reason = index.ambiguous().contains(filename)
                            ? Reason.AMBIGUOUS_FILENAME : Reason.NO_CANONICAL_SHIP;
                    details.add(new Detail(filename, null, Disposition.UNMATCHED, reason,
                            reason == Reason.AMBIGUOUS_FILENAME
                                    ? "Multiple canonical Ships use this filename"
                                    : "No canonical Ship uses this filename"));
                    continue;
                }
                matched++;
                ShipArtworkArchive.LegacyIdentity legacyIdentity = ShipArtworkArchive.LegacyIdentity.from(ship);
                Reason reason;
                if (current.contains(ShipArtworkArchive.Identity.from(ship))) {
                    reason = Reason.ALREADY_CURRENT;
                } else if (existing.contains(legacyIdentity)) {
                    reason = Reason.ALREADY_MIGRATED;
                } else {
                    migrated.put(legacyIdentity, image);
                    reason = Reason.MIGRATED;
                }
                details.add(new Detail(filename, ship.getName(), Disposition.MATCHED, reason,
                        switch (reason) {
                            case ALREADY_CURRENT -> "Current versioned artwork already exists";
                            case ALREADY_MIGRATED -> "Stale legacy artwork already exists";
                            default -> "Migrated as stale artwork";
                        }));
            }
        } catch (IOException unreadableArchive) {
            archiveError = explanation(unreadableArchive);
            System.getLogger(LegacyArtworkMigration.class.getName()).log(System.Logger.Level.WARNING,
                    "Cannot read legacy Ship Artwork: " + archive, unreadableArchive);
        }
        return new Result(migrated, new Outcome(true, matched, migrated.size(), unreadable,
                unmatched, details, archiveError));
    }

    /** Returns exact source filenames that identify only one current canonical Ship. */
    static Map<String, Ship> uniqueShips(GameData gameData) {
        return index(gameData).unique();
    }

    /** Keeps the structured reason useful even when an I/O exception has no message. */
    private static String explanation(IOException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    /** Builds an exact, collision-aware filename index from the supplied GameData only. */
    private static ShipIndex index(GameData gameData) {
        Map<String, Ship> byFilename = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (Ship ship : gameData.ships()) {
            String filename = ship.getIconName();
            if (byFilename.putIfAbsent(filename, ship) != null) ambiguous.add(filename);
        }
        ambiguous.forEach(byFilename::remove);
        return new ShipIndex(byFilename, ambiguous);
    }
}
