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
import java.io.InputStream;
import java.net.URI;

import com.kor.admiralty.Globals;

/**
 * Reads GameData bytes from the established GitHub HTTPS update location.
 */
final class GitHubGameDataRefreshSource implements GameDataRefreshSource {

    /**
     * Opens one file beneath the established remote data directory.
     *
     * @param filename module-selected remote filename
     * @return newly opened HTTPS response stream
     * @throws IOException if the URL or response stream cannot be opened
     */
    private static InputStream openRemote(String filename) throws IOException {
        String remoteUrl = String.format(Globals.URL_UPDATE, "data/" + filename);
        return URI.create(remoteUrl).toURL().openStream();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream openManifest() throws IOException {
        return openRemote(Globals.FILENAME_HASHES);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream openGameData(String filename) throws IOException {
        return openRemote(filename);
    }
}
