/*******************************************************************************
 * Copyright (C) 2015, 2019 Dave Kor
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/
package com.kor.admiralty.ui.workers;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingWorker;

public class FileDownloader extends SwingWorker<Boolean, Boolean> {

    protected static final Logger LOGGER = Logger.getGlobal();

    protected Path dataDirectory;
    protected String filename;
    protected String remoteName;

    /**
     * Creates a download rooted in the application data directory.
     *
     * @param dataDirectory directory receiving the downloaded file
     * @param filename filename beneath the data directory
     * @param remoteName absolute URL supplying the file contents
     */
    public FileDownloader(Path dataDirectory, String filename, String remoteName) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.filename = Objects.requireNonNull(filename, "filename");
        this.remoteName = Objects.requireNonNull(remoteName, "remoteName");
    }

    @Override
    protected Boolean doInBackground() {
        try {
            URL url = new URL(remoteName);
            try (InputStream input = url.openStream()) {
                Files.copy(input, dataDirectory.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (MalformedURLException cause) {
            LOGGER.log(Level.WARNING, "Malformed URL: " + remoteName, cause);
            return false;
        } catch (IOException cause) {
            LOGGER.log(Level.WARNING, "Error while downloading " + remoteName, cause);
            return false;
        }
        return true;
    }

}
