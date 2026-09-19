/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.resources;

import com.kor.admiralty.io.GameData;
import com.kor.admiralty.io.GameDataLoadException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Captures legacy archive behavior before migration replaces the Icon Cache.
 * Destructive legacy recovery is exercised only against temporary fixture copies.
 */
class LegacyArtworkArchiveTest {

    private static final String SHUTTLE_KEY = "Class_F_Shuttle.png";
    private static final String BIRD_OF_PREY_KEY = "Brel_Bird_of_Prey.png";
    private static final String UNMATCHED_KEY = "Retired_Prototype.png";
    private static final String UNREADABLE_KEY = "Broken_Artwork.png";

    @TempDir
    Path tempDir;

    /**
     * Preserves exact legacy names, composed dimensions and pixels without rewriting
     * a successfully loaded archive.
     *
     * @throws IOException if fixture copying, loading or inspection fails
     * @throws GameDataLoadException if repository reference data cannot be loaded
     */
    @Test
    void recognizableEntriesLoadTheirOriginalComposedPixels() throws IOException, GameDataLoadException {
        Path archive = copyFixture("recognizable.zip");
        byte[] original = Files.readAllBytes(archive);
        GameData gameData = GameData.load(Path.of("data"));
        assertEquals(SHUTTLE_KEY, gameData.ship("Class F Shuttle").getIconName());
        assertEquals(BIRD_OF_PREY_KEY, gameData.ship("B'rel Bird-of-Prey").getIconName());
        IconCache cache = new IconCache(tempDir);

        cache.load();

        assertArchivedPixels(archive, SHUTTLE_KEY, cache.get(SHUTTLE_KEY));
        assertArchivedPixels(archive, BIRD_OF_PREY_KEY, cache.get(BIRD_OF_PREY_KEY));
        assertNull(cache.get("class_f_shuttle.png"), "Legacy lookup uses exact entry names");
        cache.save();
        assertArrayEquals(original, Files.readAllBytes(archive));
        assertFalse(Files.exists(tempDir.resolve("newicons.zip")));
    }

    /**
     * Records that the current loader accepts readable images without consulting
     * canonical Ship identity, including entries future migration cannot map.
     *
     * @throws IOException if fixture copying or inspection fails
     * @throws GameDataLoadException if repository reference data cannot be loaded
     */
    @Test
    void readableUnmatchedEntriesAreRetainedAlongsideRecognizableEntries() throws IOException, GameDataLoadException {
        Path archive = copyFixture("recognizable-and-unmatched.zip");
        GameData gameData = GameData.load(Path.of("data"));
        assertTrue(gameData.ships().stream().noneMatch(ship -> UNMATCHED_KEY.equals(ship.getIconName())));
        IconCache cache = new IconCache(tempDir);

        cache.load();

        assertArchivedPixels(archive, SHUTTLE_KEY, cache.get(SHUTTLE_KEY));
        assertArchivedPixels(archive, UNMATCHED_KEY, cache.get(UNMATCHED_KEY));
        assertTrue(Files.exists(archive));
    }

    /**
     * Records the current all-or-nothing recovery: one unreadable image discards
     * the archive and every loaded or previously cached icon, then requests refresh.
     *
     * @throws IOException if the temporary fixture cannot be prepared
     */
    @Test
    void unreadableEntryDiscardsEvenEarlierReadableEntriesAndMarksCacheStale() throws IOException {
        Path archive = copyFixture("recognizable-and-unreadable.zip");
        IconCache cache = new IconCache(tempDir);
        cache.put("Previously_Cached.png", new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)));

        assertDoesNotThrow(cache::load);

        assertNull(cache.get(SHUTTLE_KEY));
        assertNull(cache.get(UNREADABLE_KEY));
        assertNull(cache.get("Previously_Cached.png"));
        assertFalse(Files.exists(archive));
        assertTrue(cache.isStale());
        cache.save();
        assertFalse(Files.exists(archive), "Recovery does not persist a partial archive");
    }

    /**
     * Copies an immutable classpath fixture into the isolated persistence directory.
     *
     * @return the temporary legacy archive path
     * @throws IOException if the fixture cannot be copied
     */
    private Path copyFixture(String name) throws IOException {
        Path archive = tempDir.resolve("icons.zip");
        try (InputStream input = getClass().getResourceAsStream("/ship-artwork/legacy/" + name)) {
            assertNotNull(input, "Missing legacy fixture: " + name);
            Files.copy(input, archive);
        }
        return archive;
    }

    /**
     * Compares loaded artwork to the frozen archive pixels, including transparency.
     *
     * @throws IOException if the archived reference image cannot be decoded
     */
    private static void assertArchivedPixels(Path archive, String key, ImageIcon actual) throws IOException {
        assertNotNull(actual, key);
        assertEquals(64, actual.getIconWidth(), key);
        assertEquals(64, actual.getIconHeight(), key);
        try (ZipFile zip = new ZipFile(archive.toFile());
             InputStream input = zip.getInputStream(zip.getEntry(key))) {
            BufferedImage expected = ImageIO.read(input);
            BufferedImage loaded = (BufferedImage) actual.getImage();
            assertArrayEquals(expected.getRGB(0, 0, 64, 64, null, 0, 64),
                    loaded.getRGB(0, 0, 64, 64, null, 0, 64), key);
        }
    }
}
