/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.kor.admiralty.ui.resources;

import static com.kor.admiralty.Globals.UPDATE_INTERVAL;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.Arrays;

import javax.swing.ImageIcon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Specifies the directory-backed Icon Cache through its public UI-resource seam.
 */
class IconCacheTest {

	private static final String ICON_KEY = "Test_Ship.png";
	private static final int EXPECTED_PIXEL = 0x7FFF3366;

	@TempDir
	Path tempDir;

	/**
	 * Verifies a generated image survives the persisted zip format under the same key.
	 *
	 * @throws IOException if the temporary cache cannot be written or read
	 */
	@Test
	void savedImageLoadsUnderSameKey() throws IOException {
		BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(1, 1, EXPECTED_PIXEL);
		IconCache original = new IconCache(tempDir);

		original.put(ICON_KEY, new ImageIcon(image));
		original.save();
		IconCache loaded = new IconCache(tempDir);
		loaded.load();

		ImageIcon loadedIcon = loaded.get(ICON_KEY);
		assertNotNull(loadedIcon);
		assertTrue(loaded.contains(ICON_KEY));
		assertEquals(2, loadedIcon.getIconWidth());
		assertEquals(2, loadedIcon.getIconHeight());
		assertEquals(EXPECTED_PIXEL, ((BufferedImage)loadedIcon.getImage()).getRGB(1, 1));
	}

	/**
	 * Verifies a non-atomic replacement installs the completed zip when atomic overwrite is rejected.
	 *
	 * @throws IOException if either cache version cannot be written or read
	 */
	@Test
	void secondSaveFallsBackWhenAtomicMoveCannotOverwriteExistingCache() throws IOException {
		BufferedImage originalImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		IconCache cache = new IconCache(tempDir, IconCacheTest::moveRejectingAtomicOverwrite);
		cache.put(ICON_KEY, new ImageIcon(originalImage));
		cache.save();
		BufferedImage replacementImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		replacementImage.setRGB(0, 0, EXPECTED_PIXEL);

		cache.put(ICON_KEY, new ImageIcon(replacementImage));
		assertDoesNotThrow(cache::save);
		IconCache loaded = new IconCache(tempDir);
		loaded.load();

		assertEquals(EXPECTED_PIXEL, ((BufferedImage)loaded.get(ICON_KEY).getImage()).getRGB(0, 0));
	}

	/**
	 * Simulates a provider that supports atomic moves but rejects replacing an existing target atomically.
	 *
	 * @param source path to move
	 * @param target destination path
	 * @param options requested move options
	 * @return the destination path
	 * @throws IOException if the simulated or real move fails
	 */
	private static Path moveRejectingAtomicOverwrite(Path source, Path target, CopyOption... options)
			throws IOException {
		if (Files.exists(target) && Arrays.asList(options).contains(StandardCopyOption.ATOMIC_MOVE)) {
			throw new FileAlreadyExistsException(target.toString());
		}
		return Files.move(source, target, options);
	}

	/**
	 * Verifies first run requests icon refresh when no persisted cache exists.
	 */
	@Test
	void missingCacheFileIsStale() {
		IconCache cache = new IconCache(tempDir);

		assertTrue(cache.isStale());
	}

	/**
	 * Verifies a recently modified cache does not schedule another background refresh.
	 *
	 * @throws IOException if the temporary cache marker cannot be created
	 */
	@Test
	void recentCacheFileIsFresh() throws IOException {
		Files.createFile(tempDir.resolve("icons.zip"));
		IconCache cache = new IconCache(tempDir);

		assertFalse(cache.isStale());
	}

	/**
	 * Verifies an old cache reports stale once and advances its timestamp for the next interval.
	 *
	 * @throws IOException if the temporary cache timestamp cannot be prepared or inspected
	 */
	@Test
	void oldCacheFileIsStaleAndTouchedForOneInterval() throws IOException {
		Path cacheFile = Files.createFile(tempDir.resolve("icons.zip"));
		long oldTimestamp = System.currentTimeMillis() - UPDATE_INTERVAL - Duration.ofHours(1).toMillis();
		Files.setLastModifiedTime(cacheFile, FileTime.fromMillis(oldTimestamp));
		IconCache cache = new IconCache(tempDir);

		assertTrue(cache.isStale());
		assertTrue(Files.getLastModifiedTime(cacheFile).toMillis() > oldTimestamp);
		assertFalse(cache.isStale());
	}

	/**
	 * Verifies a failed replacement write cannot destroy the previously persisted Icon Cache.
	 *
	 * @throws IOException if the initial cache or failure fixture cannot be prepared or reread
	 */
	@Test
	void failedSaveLeavesExistingCacheIntact() throws IOException {
		BufferedImage oldImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		IconCache original = new IconCache(tempDir);
		original.put(ICON_KEY, new ImageIcon(oldImage));
		original.save();
		Files.createDirectory(tempDir.resolve("newicons.zip"));
		IconCache update = new IconCache(tempDir);
		update.load();
		update.put("Replacement.png", new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)));

		assertThrows(IOException.class, update::save);
		IconCache preserved = new IconCache(tempDir);
		preserved.load();
		assertNotNull(preserved.get(ICON_KEY));
		assertNull(preserved.get("Replacement.png"));
	}
}
