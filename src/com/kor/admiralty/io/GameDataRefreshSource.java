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

/**
 * Supplies remote GameData content without receiving or selecting local
 * destinations.
 */
interface GameDataRefreshSource {

    /**
     * Opens the remote digest manifest as raw bytes. The refresh module owns the
     * returned stream and its UTF-8 decoding.
     *
     * @return newly opened manifest stream
     * @throws IOException if the response cannot be opened
     */
    InputStream openManifest() throws IOException;

    /**
     * Opens one module-selected GameData file as raw bytes.
     *
     * @param filename fixed GameData filename selected by the refresh module
     * @return newly opened content stream
     * @throws IOException if the response cannot be opened
     */
    InputStream openGameData(String filename) throws IOException;
}
