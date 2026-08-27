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
package com.kor.admiralty.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Locale;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBException;

import com.kor.admiralty.Globals;
import com.kor.admiralty.beans.AdmAssignment;
import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.Admirals;
import com.kor.admiralty.beans.Event;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.ui.resources.IconCache;
import com.kor.admiralty.ui.workers.SwingWorkerExecutor;

import static com.kor.admiralty.Globals.FILENAME_ADMIRALS;
import static com.kor.admiralty.Globals.FILENAME_ICONCACHE;
import static com.kor.admiralty.ui.resources.Strings.ExceptionDialog.*;

public class Datastore {

	private static final Logger logger = Logger.getGlobal();
	private static SortedMap<String, File> FILES = new TreeMap<String, File>();
	private static SortedMap<String, Ship> SHIPS = new TreeMap<String, Ship>();
	private static SortedMap<String, Event> EVENTS = new TreeMap<String, Event>();
	private static SortedMap<String, AdmAssignment> ASSIGNMENTS = new TreeMap<String, AdmAssignment>();
	private static GameData GAME_DATA = null;
	private static boolean GAME_DATA_LOADED = false;
	private static Admirals ADMIRALS = null;

	private static final AdmiralsStore ADMIRALS_STORE = createAdmiralsStore();
	private static final File FOLDER_CURRENT = file(".");
	private static final IconCache ICON_CACHE = createIconCache();
	
	public static File getCurrentFolder() {
		return FOLDER_CURRENT;
	}

	public static SortedMap<String, Ship> getAllShips() {
		getGameData();
		return SHIPS;
	}
	
	public static SortedMap<String, Event> getEvents() {
		getGameData();
		return EVENTS;
	}

	public static SortedMap<String, AdmAssignment> getAssignments() {
		getGameData();
		return ASSIGNMENTS;
	}
	
	public static boolean isDataFilesStale() {
		File file = file(Globals.FILENAME_HASHES);
		return file.exists() ? isStale(file) : true;
	}

	/**
	 * Returns the transitional process-wide Icon Cache used by legacy UI wiring.
	 *
	 * @return the Icon Cache rooted in the current legacy data directory
	 */
	public static IconCache getIconCache() {
		return ICON_CACHE;
	}
	
	/**
	 * Loads one GameData from the working directory and adapts its values to legacy maps.
	 * Load failures retain the existing warning-and-empty-data behavior until bootstrap owns errors.
	 *
	 * @return the single GameData instance used by the legacy application wiring
	 */
	private static GameData getGameData() {
		if (GAME_DATA != null) {
			return GAME_DATA;
		}

		try {
			GAME_DATA = GameData.load(FOLDER_CURRENT.toPath());
			GAME_DATA_LOADED = true;
		} catch (GameDataLoadException cause) {
			logger.log(Level.WARNING, String.format(ErrorReading, FOLDER_CURRENT.getAbsolutePath()), cause);
			GAME_DATA = GameData.builder().build();
			GAME_DATA_LOADED = false;
		}

		SHIPS.clear();
		for (Ship ship : GAME_DATA.ships()) {
			SHIPS.put(ship.getName().toLowerCase(Locale.ROOT), ship);
		}
		EVENTS.clear();
		for (Event event : GAME_DATA.events()) {
			EVENTS.put(event.getName().toLowerCase(Locale.ROOT), event);
		}
		ASSIGNMENTS.clear();
		for (AdmAssignment assignment : GAME_DATA.assignments()) {
			ASSIGNMENTS.put(assignment.getName().toLowerCase(Locale.ROOT), assignment);
		}
		return GAME_DATA;
	}
	
	/*/
	private static long getCacheTime() {
		return System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
	}
	//*/

	public static Admirals getAdmirals() {
		if (ADMIRALS == null) {
			GameData gameData = getGameData();
			try {
				ADMIRALS = loadAdmirals(file(FILENAME_ADMIRALS));
			} catch (JAXBException cause) {
				throw new IllegalStateException(String.format(ErrorReading, FILENAME_ADMIRALS), cause);
			}
			ADMIRALS.attach(gameData);
			for (Admiral admiral : ADMIRALS.getAdmirals()) {
				// Validation is destructive, so preserve saved names when the atomic GameData load failed.
				if (GAME_DATA_LOADED) {
					admiral.validateShips();
				}
				admiral.activateShips();
			}
			if (isDataFilesStale()) {
				SwingWorkerExecutor.updateDataFiles();
			}
			try {
				if (ICON_CACHE.isStale()) {
					// Download icons for ships owned by the user
					// As there can potentially be hundreds of icons to download,
					// the update is done in the background.
					// As a result, the UI may not always be up to date for the current run.
					for (Ship ship : getAllShips().values()) {
						if (ship.isOwned()) SwingWorkerExecutor.downloadIcon(ship);
					}
				}
			} catch (UncheckedIOException cause) {
				// Timestamp metadata must not make the legacy UI unlaunchable before AppBootstrap owns failures.
				logger.log(Level.WARNING, String.format(ErrorReading, FILENAME_ICONCACHE), cause);
			}
		}
		return ADMIRALS;
	}
	
	public static void setAdmirals(Admirals admirals) {
		try {
			saveAdmirals(file(FILENAME_ADMIRALS), admirals);
		} catch (JAXBException cause) {
			logger.log(Level.WARNING, String.format(ErrorWriting, FILENAME_ADMIRALS), cause);
		}
	}

	/**
	 * Loads or creates Admirals at an exact legacy file path through AdmiralsStore.
	 *
	 * @param file legacy Admirals XML path
	 * @return loaded or default Admirals container
	 * @throws JAXBException if the XML cannot be created or loaded
	 */
	public static Admirals loadAdmirals(File file) throws JAXBException {
		return ADMIRALS_STORE.loadOrCreateFile(file.toPath());
	}

	/**
	 * Saves Admirals at an exact legacy file path through AdmiralsStore.
	 *
	 * @param file legacy Admirals XML path
	 * @param admirals container to persist
	 * @throws JAXBException if the XML cannot be written
	 */
	public static void saveAdmirals(File file, Admirals admirals) throws JAXBException {
		ADMIRALS_STORE.saveFile(file.toPath(), admirals);
	}

	/**
	 * Preserves the legacy export facade while delegating file behavior to AdmiralsStore.
	 *
	 * @param file target text file
	 * @param ships Ships whose display names are exported
	 * @return whether the file was written successfully
	 */
	public static boolean exportShips(File file, Collection<Ship> ships) {
		return ADMIRALS_STORE.exportShipNames(file, ships);
	}

	/**
	 * Preserves the legacy import facade while supplying the current GameData to AdmiralsStore.
	 *
	 * @param file source text file
	 * @param admiral Admiral receiving canonical active Ship names
	 * @return recognized line count, or {@code -1} on an I/O failure
	 */
	public static int importShips(File file, Admiral admiral) {
		return ADMIRALS_STORE.importShipNames(file, getGameData(), admiral);
	}

	/**
	 * Constructs the transitional singleton store and fails class initialization when JAXB is unavailable.
	 */
	private static AdmiralsStore createAdmiralsStore() {
		try {
			return new AdmiralsStore();
		} catch (JAXBException cause) {
			throw new ExceptionInInitializerError(cause);
		}
	}

	/**
	 * Creates and loads the shared Icon Cache using the legacy data directory until AppBootstrap owns composition.
	 * Load failures are logged and leave the returned cache empty so existing startup behavior remains unchanged.
	 *
	 * @return the shared Icon Cache, possibly empty after a logged load failure
	 */
	private static IconCache createIconCache() {
		IconCache iconCache = new IconCache(FOLDER_CURRENT.toPath());
		try {
			iconCache.load();
		} catch (IOException cause) {
			logger.log(Level.WARNING, String.format(ErrorReading, FILENAME_ICONCACHE), cause);
		}
		return iconCache;
	}
	
	public static File file(String filename) {
		if (!FILES.containsKey(filename)) {
			FILES.put(filename, new File(filename));
		}
		return FILES.get(filename);
	}

	private static Reader loadFile(File file) {
		if (file.exists()) {
			try {
				return new FileReader(file);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	public static boolean isFresh(File file) {
		return Globals.isTimestampFresh(file.lastModified());
	}
	
	public static boolean isStale(File file) {
		return Globals.isTimestampStale(file.lastModified());
	}
	
	public static void copy(Reader input, Writer output) throws IOException {
		char[] buffer = new char[1024];
		int n = 0;
		while (-1 != (n = input.read(buffer))) {
			output.write(buffer, 0, n);
		}
	}
	
}
