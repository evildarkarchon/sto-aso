/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Uses the standard Java filesystem provider for production GameData and
 * manifest replacement.
 */
final class FileSystemGameDataRefreshReplacement implements GameDataRefreshReplacement {

    /** {@inheritDoc} */
    @Override
    public void replaceGameData(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** {@inheritDoc} */
    @Override
    public void replaceManifest(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
