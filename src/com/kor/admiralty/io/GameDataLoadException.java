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
package com.kor.admiralty.io;

import java.io.Serial;
import java.nio.file.Path;

/**
 * Reports a checked failure while loading required GameData files.
 */
public class GameDataLoadException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Path path;

    /**
     * Creates a failure for the file or directory that could not be loaded.
     *
     * @param path  the source path that failed
     * @param cause the underlying I/O or parse failure
     */
    GameDataLoadException(Path path, Throwable cause) {
        super("Unable to load required GameData path: " + path, cause);
        this.path = path;
    }

    /**
     * Returns the source path associated with the load failure.
     *
     * @return the failed file or directory
     */
    public Path getPath() {
        return path;
    }
}
