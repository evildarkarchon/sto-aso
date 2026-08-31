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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingWorker;

/**
 * Copies one remote file into a caller-supplied application data directory on a Swing worker thread.
 */
public class FileDownloader extends SwingWorker<Boolean, Boolean> {

    protected static final Logger LOGGER = Logger.getGlobal();

    protected Path dataDirectory;
    protected String filename;
    protected String remoteUrl;

    /**
     * Creates a download rooted in the application data directory.
     *
     * @param dataDirectory directory receiving the downloaded file
     * @param filename filename beneath the data directory
     * @param remoteUrl absolute URL supplying the file contents
     */
    public FileDownloader(Path dataDirectory, String filename, String remoteUrl) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.filename = Objects.requireNonNull(filename, "filename");
        this.remoteUrl = Objects.requireNonNull(remoteUrl, "remoteUrl");
    }

    /**
     * Replaces the destination with the remote bytes using the standard-library copy operation.
     *
     * @return {@code true} when the complete file was copied, otherwise {@code false} after logging the failure
     */
    @Override
    protected Boolean doInBackground() {
        try {
            URL url = new URI(remoteUrl).toURL();
            try (InputStream input = url.openStream()) {
                Files.copy(input, dataDirectory.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (MalformedURLException | URISyntaxException cause) {
            LOGGER.log(Level.WARNING, "Malformed URL: " + remoteUrl, cause);
            return false;
        } catch (IOException cause) {
            LOGGER.log(Level.WARNING, "Error while downloading " + remoteUrl, cause);
            return false;
        }
        return true;
    }

}
