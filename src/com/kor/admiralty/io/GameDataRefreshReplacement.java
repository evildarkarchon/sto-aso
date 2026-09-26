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
import java.nio.file.CopyOption;
import java.nio.file.Path;

/**
 * Replaces verified live GameData and publishes its validated manifest without
 * exposing staging, backup, or transaction policy.
 */
interface GameDataRefreshReplacement {

    /**
     * Replaces one live GameData file with its verified staged content.
     *
     * @param source verified staged file
     * @param target live GameData destination
     * @throws IOException if the replacement cannot be completed
     */
    void replaceGameData(Path source, Path target) throws IOException;

    /**
     * Publishes the validated manifest after every changed GameData replacement.
     *
     * @param source  staged validated manifest
     * @param target  live manifest commit point
     * @param options filesystem replacement semantics requested by the transaction
     * @throws IOException if the replacement cannot be completed
     */
    void replaceManifest(Path source, Path target, CopyOption... options) throws IOException;
}
