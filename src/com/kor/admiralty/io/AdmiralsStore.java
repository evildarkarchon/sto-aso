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

import static com.kor.admiralty.Globals.FILENAME_ADMIRALS;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.Admirals;
import com.kor.admiralty.beans.Ship;

/**
 * Persists Admirals and their Ship-name lists without assuming a working directory.
 */
public class AdmiralsStore {

    private final Marshaller marshaller;
    private final Unmarshaller unmarshaller;

    /**
     * Builds the JAXB machinery eagerly so configuration failures surface during application startup.
     *
     * @throws JAXBException if the Admirals JAXB context cannot be initialized
     */
    public AdmiralsStore() throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(Admirals.class);
        marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        unmarshaller = context.createUnmarshaller();
    }

    /**
     * Resolves the fixed Admirals filename without consulting process-global filesystem state.
     */
    private static Path admiralsFile(Path directory) {
        return Objects.requireNonNull(directory, "directory").resolve(FILENAME_ADMIRALS);
    }

    /**
     * Loads Admirals from the configured filename in a directory, creating and persisting defaults on first use.
     * Loaded Admirals are deliberately not attached to GameData; the caller owns that startup step.
     *
     * @param directory directory containing the Admirals XML file
     * @return persisted Admirals, or one default Admiral when the file did not exist
     * @throws JAXBException if the file cannot be created or read as Admirals XML
     */
    public Admirals loadOrCreate(Path directory) throws JAXBException {
        return loadOrCreateFile(admiralsFile(directory));
    }

    /**
     * Transitional exact-file adapter for legacy Datastore callers pending bootstrap removal.
     *
     * @param file exact Admirals XML path used by the legacy facade
     * @return loaded or newly persisted default Admirals
     * @throws JAXBException if the file cannot be created or read as Admirals XML
     */
    Admirals loadOrCreateFile(Path file) throws JAXBException {
        Objects.requireNonNull(file, "file");
        if (Files.notExists(file)) {
            Admirals admirals = new Admirals();
            saveFile(file, admirals);
            return admirals;
        }
        return (Admirals) unmarshaller.unmarshal(file.toFile());
    }

    /**
     * Saves Admirals to the configured filename in a caller-supplied directory.
     *
     * @param directory directory that receives the Admirals XML file
     * @param admirals Admirals container to persist
     * @throws JAXBException if the container cannot be marshalled
     */
    public void save(Path directory, Admirals admirals) throws JAXBException {
        saveFile(admiralsFile(directory), admirals);
    }

    /**
     * Transitional exact-file adapter preserving the legacy Datastore save signature.
     *
     * @param file exact Admirals XML path used by the legacy facade
     * @param admirals Admirals container to persist
     * @throws JAXBException if the container cannot be marshalled
     */
    void saveFile(Path file, Admirals admirals) throws JAXBException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(admirals, "admirals");
        marshaller.marshal(admirals, file.toFile());
    }

    /**
     * Exports Ship display names using the legacy text format and platform encoding.
     *
     * @param file text file that receives one display name per line
     * @param ships Ships to export in collection iteration order
     * @return {@code true} when the complete list was written; {@code false} when the file could not be opened
     */
    public boolean exportShipNames(File file, Collection<Ship> ships) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(ships, "ships");
        try (PrintStream output = new PrintStream(file)) {
            for (Ship ship : ships) {
                output.println(ship.getDisplayName());
            }
            return true;
        } catch (FileNotFoundException cause) {
            cause.printStackTrace();
            return false;
        }
    }

    /**
     * Imports known Ship names into an Admiral, resolving case and renames through GameData.
     * Recognized lines are counted as before even when the Admiral already contains the Ship.
     *
     * @param file text file containing one Ship name per line
     * @param gameData reference data used to resolve each line
     * @param admiral Admiral that receives canonical active Ship names
     * @return number of recognized lines, or {@code -1} when the file cannot be read
     */
    public int importShipNames(File file, GameData gameData, Admiral admiral) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(gameData, "gameData");
        Objects.requireNonNull(admiral, "admiral");
        int importedCount = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Ship ship = gameData.ship(line.trim());
                if (ship != null) {
                    admiral.addActive(ship.getName());
                    importedCount++;
                }
            }
            return importedCount;
        } catch (IOException cause) {
            cause.printStackTrace();
            return -1;
        }
    }
}
