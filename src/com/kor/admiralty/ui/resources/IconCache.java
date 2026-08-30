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

import static com.kor.admiralty.Globals.FILENAME_ICONCACHE;
import static com.kor.admiralty.Globals.FILENAME_NEWCACHE;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

/**
 * Owns composed Ship icons and persists them beneath a caller-supplied data directory.
 * Cache operations are synchronized because background downloads and Swing rendering share one instance.
 */
public class IconCache {

    private static final long ICON_CACHE_UPDATE_INTERVAL = Duration.ofDays(7).toMillis();
    private static final Logger LOGGER = Logger.getLogger(IconCache.class.getName());
    private final Path cacheFile;
    private final Path replacementFile;
    private final FileMover fileMover;
    private final SortedMap<String, ImageIcon> icons = new TreeMap<String, ImageIcon>();
    private boolean changed;
    private boolean recoveryNeeded;

    /**
     * Creates an empty Icon Cache whose persistence paths are resolved beneath a data directory.
     *
     * @param dataDirectory directory containing {@code icons.zip} and its temporary replacement
     * @throws NullPointerException if {@code dataDirectory} is {@code null}
     */
    public IconCache(Path dataDirectory) {
        this(dataDirectory, Files::move);
    }

    /**
     * Creates an Icon Cache with the file-move implementation supplied by its filesystem boundary.
     *
     * @param dataDirectory directory containing {@code icons.zip} and its temporary replacement
     * @param fileMover operation used to install a completed replacement zip
     * @throws NullPointerException if {@code dataDirectory} or {@code fileMover} is {@code null}
     */
    IconCache(Path dataDirectory, FileMover fileMover) {
        Path directory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        cacheFile = directory.resolve(FILENAME_ICONCACHE);
        replacementFile = directory.resolve(FILENAME_NEWCACHE);
        this.fileMover = Objects.requireNonNull(fileMover, "fileMover");
    }

    /**
     * Loads all persisted icons. An unreadable derived archive is discarded and marked stale so startup can rebuild it.
     */
    public synchronized void load() {
        if (Files.notExists(cacheFile)) {
            icons.clear();
            changed = false;
            recoveryNeeded = false;
            return;
        }

        SortedMap<String, ImageIcon> loadedIcons = new TreeMap<String, ImageIcon>();
        try (ZipFile zipFile = new ZipFile(cacheFile.toFile())) {
            for (Enumeration<? extends ZipEntry> entries = zipFile.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                try (InputStream input = zipFile.getInputStream(entry)) {
                    BufferedImage image = ImageIO.read(input);
                    if (image == null) {
                        throw new IOException("Icon cache entry is not a readable image: " + entry.getName());
                    }
                    loadedIcons.put(entry.getName(), new ImageIcon(image));
                }
            }
        } catch (IOException cause) {
            icons.clear();
            changed = false;
            recoveryNeeded = true;
            try {
                Files.deleteIfExists(cacheFile);
                LOGGER.log(Level.WARNING, "Discarded unreadable Icon Cache: " + cacheFile, cause);
            } catch (IOException discardCause) {
                // A failed cleanup must not turn derived cache corruption into a fatal startup error.
                discardCause.addSuppressed(cause);
                LOGGER.log(Level.WARNING, "Unable to discard unreadable Icon Cache: " + cacheFile, discardCause);
            }
            return;
        }

        icons.clear();
        icons.putAll(loadedIcons);
        changed = false;
        recoveryNeeded = false;
    }

    /**
     * Returns the composed icon stored under an exact cache key.
     *
     * @param key icon filename used as the zip entry name
     * @return cached icon, or {@code null} when absent
     */
    public synchronized ImageIcon get(String key) {
        return icons.get(key);
    }

    /**
     * Reports whether an exact icon key is already cached.
     *
     * @param key icon filename used as the zip entry name
     * @return {@code true} when the cache contains the key
     */
    public synchronized boolean contains(String key) {
        return icons.containsKey(key);
    }

    /**
     * Stores a composed icon and marks the cache for persistence on exit.
     * Persisted composed icons must be backed by {@link BufferedImage} instances.
     *
     * @param key icon filename used as the zip entry name
     * @param icon composed Ship icon to cache
     * @throws NullPointerException if {@code key} or {@code icon} is {@code null}
     * @throws IllegalArgumentException if the icon is not backed by a buffered image
     */
    public synchronized void put(String key, ImageIcon icon) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(icon, "icon");
        if (!(icon.getImage() instanceof BufferedImage)) {
            throw new IllegalArgumentException("Cached icons must be backed by BufferedImage");
        }
        icons.put(key, icon);
        changed = true;
    }

    /**
     * Reports whether the persisted Icon Cache needs a background refresh.
     *
     * @return {@code true} when the cache file is missing or older than the update interval
     * @throws UncheckedIOException if an existing cache timestamp cannot be inspected or touched
     */
    public synchronized boolean isStale() {
        if (recoveryNeeded) {
            return true;
        }
        if (Files.notExists(cacheFile)) {
            return true;
        }
        try {
            if (isTimestampStale(Files.getLastModifiedTime(cacheFile).toMillis())) {
                // Touching records that refresh was scheduled, so repeated checks fire only once per interval.
                Files.setLastModifiedTime(cacheFile, FileTime.fromMillis(System.currentTimeMillis()));
                return true;
            }
            return false;
        } catch (IOException cause) {
            throw new UncheckedIOException("Cannot inspect or touch Icon Cache timestamp: " + cacheFile, cause);
        }
    }

    /**
     * Writes changed icons to a complete replacement zip, preferring atomic installation with a replacement fallback.
     *
     * @throws IOException if the replacement cannot be written or installed
     */
    public synchronized void save() throws IOException {
        if (!changed) {
            return;
        }

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(replacementFile))) {
            for (Map.Entry<String, ImageIcon> entry : icons.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                try {
                    BufferedImage image = (BufferedImage) entry.getValue().getImage();
                    if (!ImageIO.write(image, "png", zip)) {
                        throw new IOException("No PNG writer is available for icon: " + entry.getKey());
                    }
                } finally {
                    zip.closeEntry();
                }
            }
        }

        try {
            fileMover.move(
                    replacementFile,
                    cacheFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException | FileAlreadyExistsException cause) {
            // ATOMIC_MOVE may ignore REPLACE_EXISTING, so retry the completed zip with explicit replacement.
            fileMover.move(replacementFile, cacheFile, StandardCopyOption.REPLACE_EXISTING);
        }
        changed = false;
        recoveryNeeded = false;
    }

    /**
     * Applies the Icon Cache's seven-day freshness boundary to a persisted timestamp.
     *
     * @param timestamp cache modification time in epoch milliseconds
     * @return {@code true} at or beyond the refresh interval
     */
    private static boolean isTimestampStale(long timestamp) {
        return timestamp <= System.currentTimeMillis() - ICON_CACHE_UPDATE_INTERVAL;
    }

    /**
     * Moves a completed cache replacement using filesystem-provider semantics.
     */
    @FunctionalInterface
    interface FileMover {

        /**
         * Moves one path to another with the requested copy options.
         *
         * @param source path to move
         * @param target destination path
         * @param options move options understood by the filesystem provider
         * @return the destination path
         * @throws IOException if the move fails
         */
        Path move(Path source, Path target, CopyOption... options) throws IOException;
    }
}
