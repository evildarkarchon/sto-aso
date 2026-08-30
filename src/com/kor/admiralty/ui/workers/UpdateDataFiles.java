package com.kor.admiralty.ui.workers;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingWorker;

import com.kor.admiralty.Globals;

import static com.kor.admiralty.Globals.FILENAME_ASSIGNMENTS;
import static com.kor.admiralty.Globals.FILENAME_EVENTS;
import static com.kor.admiralty.Globals.FILENAME_RENAMED;
import static com.kor.admiralty.Globals.FILENAME_SHIPCACHE;
import static com.kor.admiralty.Globals.FILENAME_TRAITS;
import static com.kor.admiralty.ui.resources.Strings.ExceptionDialog.*;

public class UpdateDataFiles extends SwingWorker<UpdateDataFiles.Result, Boolean> {

    private static final long GAME_DATA_UPDATE_INTERVAL = Duration.ofDays(7).toMillis();
    private static final char[] LOWER_HEX_DIGITS = "0123456789abcdef".toCharArray();
    protected static final Logger logger = Logger.getLogger(UpdateDataFiles.class.getName());
    protected static final String URL_HASHES = url(Globals.FILENAME_HASHES);
    protected static final List<String> FILENAMES = List.of(
            FILENAME_SHIPCACHE,
            FILENAME_RENAMED,
            FILENAME_EVENTS,
            FILENAME_ASSIGNMENTS,
            FILENAME_TRAITS);

    protected Path dataDirectory;
    protected Path fileHashes;
    protected Properties hashesLocal;
    protected Properties hashesRemote;

    /**
     * Creates a download-only updater rooted in the bootstrapped application data directory.
     *
     * @param dataDirectory directory containing and receiving all GameData files
     */
    public UpdateDataFiles(Path dataDirectory) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.fileHashes = dataDirectory.resolve(Globals.FILENAME_HASHES);
    }

    protected static String url(String filename) {
        return String.format(Globals.URL_UPDATE, "data/" + filename);
    }

    /**
     * Reports whether the GameData hash manifest is missing or old enough to request a refresh.
     *
     * @param dataDirectory directory containing the hash manifest
     * @return {@code true} when startup should schedule this updater
     * @throws IOException if an existing manifest timestamp cannot be read
     */
    public static boolean isStale(Path dataDirectory) throws IOException {
        Path hashesFile = dataDirectory.resolve(Globals.FILENAME_HASHES);
        return Files.notExists(hashesFile)
                || isTimestampStale(Files.getLastModifiedTime(hashesFile).toMillis());
    }

    /**
     * Applies this updater's seven-day freshness boundary to a file timestamp.
     *
     * @param timestamp file modification time in epoch milliseconds
     * @return {@code true} at or beyond the update interval
     */
    private static boolean isTimestampStale(long timestamp) {
        return timestamp <= System.currentTimeMillis() - GAME_DATA_UPDATE_INTERVAL;
    }

    /**
     * Downloads changed GameData files sequentially on this worker thread.
     *
     * @return outcome used by {@link #done()} to report whether a restart is needed
     */
    @Override
    protected Result doInBackground() throws Exception {
        Properties localHashes = loadLocalHashes();
        Properties remoteHashes = loadRemoteHashes();
        if (!hasExpectedFiles(remoteHashes)) {
            logger.warning("Remote GameData hash manifest does not contain exactly the required filenames.");
            return Result.FAILED;
        }

        boolean successful = true;
        boolean downloaded = false;
        for (String filename : FILENAMES) {
            String localHash = localHashes.getProperty(filename, "");
            String remoteHash = remoteHashes.getProperty(filename, "");

            if (!localHash.equals(remoteHash)) {
                String url = url(filename);
                downloaded = true;
                successful &= new FileDownloader(dataDirectory, filename, url).doInBackground();
            }
        }
        if (!successful) {
            return Result.FAILED;
        }
        if (!storeLocalProperties(remoteHashes, fileHashes)) {
            return Result.FAILED;
        }
        return downloaded ? Result.DOWNLOADED : Result.CURRENT;
    }

    /**
     * Requires the remote manifest to describe the complete fixed GameData set and no caller-controlled paths.
     *
     * @param manifest untrusted remote hash manifest
     * @return {@code true} only when every required filename appears and no unexpected key is present
     */
    private static boolean hasExpectedFiles(Properties manifest) {
        return manifest.stringPropertyNames().equals(Set.copyOf(FILENAMES));
    }

    /**
     * Reports the background outcome on the event-dispatch thread and announces restart only after successful downloads.
     */
    @Override
    public void done() {
        try {
            Result result = get();
            if (result == Result.DOWNLOADED) {
                logger.info("GameData update complete; restart ASO to apply downloaded files.");
            } else if (result == Result.CURRENT) {
                logger.info("GameData files are already current.");
            } else {
                logger.warning("GameData update did not complete; downloaded files were not marked current.");
            }
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            logger.log(Level.WARNING, "Interrupted while completing the GameData update.", cause);
        } catch (ExecutionException cause) {
            logger.log(Level.WARNING, "GameData update failed before completion.", cause.getCause());
        }
    }

    /**
     * Loads or creates hashes for the GameData currently on disk.
     *
     * @return local hash manifest
     */
    protected Properties loadLocalHashes() {
        Properties properties = new Properties();
        if (Files.notExists(fileHashes)) {
            for (String filename : FILENAMES) {
                properties.setProperty(filename, hash(filename));
            }
            storeLocalProperties(properties, fileHashes);
        } else {
            loadLocalProperties(properties, fileHashes);
        }
        return properties;
    }

    /**
     * Downloads the current remote hash manifest.
     *
     * @return remote hashes, or an empty manifest when the request fails
     */
    protected Properties loadRemoteHashes() {
        Properties properties = new Properties();
        try {
            URL url = new URL(URL_HASHES);
            try (Reader reader = openRemoteHashesReader(url)) {
                properties.load(reader);
            } catch (IOException cause) {
                logger.log(Level.WARNING, String.format(ErrorReading, URL_HASHES), cause);
                // Properties.load mutates incrementally, so a failed response must not escape as a valid subset.
                properties.clear();
            }
        } catch (MalformedURLException cause) {
            logger.log(Level.WARNING, String.format(ErrorMalformedUrl, URL_HASHES), cause);
        }
        return properties;
    }

    /**
     * Opens the remote hash manifest while keeping the network boundary replaceable in focused tests.
     *
     * @param url remote hash-manifest URL
     * @return reader whose lifecycle is owned by {@link #loadRemoteHashes()}
     * @throws IOException if the connection or response stream cannot be opened
     */
    protected Reader openRemoteHashesReader(URL url) throws IOException {
        return new InputStreamReader(url.openStream());
    }

    /**
     * Reads a Java properties manifest from an exact path.
     *
     * @param properties destination manifest
     * @param file source path
     */
    protected void loadLocalProperties(Properties properties, Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        } catch (IOException cause) {
            logger.log(Level.WARNING, String.format(ErrorReading, file.getFileName()), cause);
        }
    }

    /**
     * Writes a Java properties manifest to an exact path.
     *
     * @param properties manifest to persist
     * @param file destination path
     * @return {@code true} when the complete manifest was written
     */
    protected boolean storeLocalProperties(Properties properties, Path file) {
        try (java.io.Writer writer = Files.newBufferedWriter(file)) {
            properties.store(writer, "");
            return true;
        } catch (IOException cause) {
            logger.log(Level.WARNING, String.format(ErrorWriting, file.getFileName()), cause);
            return false;
        }
    }

    /**
     * Computes the current MD5 for one GameData file beneath the configured directory.
     *
     * @param filename GameData filename
     * @return lower-case MD5, or an empty string when hashing fails
     */
    protected String hash(String filename) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(Files.readAllBytes(dataDirectory.resolve(filename)));
            byte[] digest = md.digest();
            return toLowerHex(digest);
        } catch (NoSuchAlgorithmException cause) {
            logger.log(Level.WARNING, ErrorNoMD5, cause);
        } catch (IOException cause) {
            logger.log(Level.WARNING, String.format(ErrorReading, filename), cause);
        }
        return "";
    }

    /**
     * Encodes digest bytes without depending on the JAXB utility package owned by AdmiralsStore.
     *
     * @param bytes digest bytes to encode
     * @return lower-case hexadecimal with two characters per byte
     */
    private static String toLowerHex(byte[] bytes) {
        StringBuilder encoded = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            int unsigned = value & 0xff;
            encoded.append(LOWER_HEX_DIGITS[unsigned >>> 4]);
            encoded.append(LOWER_HEX_DIGITS[unsigned & 0x0f]);
        }
        return encoded.toString();
    }

    /**
     * Distinguishes successful downloads from no-op freshness checks and incomplete updates.
     */
    protected enum Result {
        CURRENT,
        DOWNLOADED,
        FAILED
    }

}
