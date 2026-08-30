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
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBException;

import com.kor.admiralty.App;
import com.kor.admiralty.beans.AdmAssignment;
import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.Admirals;
import com.kor.admiralty.beans.Event;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.ui.resources.IconCache;

import static com.kor.admiralty.ui.resources.Strings.ExceptionDialog.*;

/**
 * Transitional compatibility facade for UI call sites that have not yet moved to constructor injection.
 * All application state is supplied by AppBootstrap; this class performs no startup loading or scheduling.
 */
public class Datastore {

    private static final Logger logger = Logger.getGlobal();
    private static final SortedMap<String, Ship> SHIPS = new TreeMap<String, Ship>();
    private static final SortedMap<String, Event> EVENTS = new TreeMap<String, Event>();
    private static final SortedMap<String, AdmAssignment> ASSIGNMENTS = new TreeMap<String, AdmAssignment>();
    private static GameData mappedGameData;
    private static AdmiralsStore admiralsStore;
    private static IconCache iconCache;

    /**
     * Installs the persistence modules loaded by AppBootstrap before background jobs can access this facade.
     * Replacing the references supports isolated bootstrap tests; production App state remains one-shot.
     *
     * @param store initialized Admirals persistence module
     * @param cache loaded process-wide Icon Cache
     */
    public static synchronized void installBootstrapState(AdmiralsStore store, IconCache cache) {
        admiralsStore = Objects.requireNonNull(store, "store");
        iconCache = Objects.requireNonNull(cache, "cache");
        mappedGameData = null;
        SHIPS.clear();
        EVENTS.clear();
        ASSIGNMENTS.clear();
    }

    /**
     * Returns the bootstrapped data directory through the legacy File-based UI contract.
     *
     * @return current application data directory
     * @throws IllegalStateException if called before AppBootstrap completes
     */
    public static File getCurrentFolder() {
        return App.dataDir().toFile();
    }

    /**
     * Returns all bootstrapped Ships under the lower-case keys expected by legacy UI callers.
     *
     * @return shared legacy Ship map backed by the current GameData objects
     * @throws IllegalStateException if called before AppBootstrap completes
     */
    public static SortedMap<String, Ship> getAllShips() {
        refreshLegacyMaps();
        return SHIPS;
    }

    /**
     * Returns all bootstrapped Events under the lower-case keys expected by legacy UI callers.
     *
     * @return shared legacy Event map backed by the current GameData objects
     * @throws IllegalStateException if called before AppBootstrap completes
     */
    public static SortedMap<String, Event> getEvents() {
        refreshLegacyMaps();
        return EVENTS;
    }

    /**
     * Returns all bootstrapped Assignments under the lower-case keys expected by legacy UI callers.
     *
     * @return shared legacy Assignment map backed by the current GameData objects
     * @throws IllegalStateException if called before AppBootstrap completes
     */
    public static SortedMap<String, AdmAssignment> getAssignments() {
        refreshLegacyMaps();
        return ASSIGNMENTS;
    }

    /**
     * Returns the transitional process-wide Icon Cache used by legacy UI wiring.
     *
     * @return the Icon Cache rooted in the current legacy data directory
     */
    public static IconCache getIconCache() {
        IconCache cache = iconCache;
        if (cache == null) {
            throw new IllegalStateException("Icon Cache is unavailable before AppBootstrap completes");
        }
        return cache;
    }

    /**
     * Adapts the bootstrapped GameData collections to the legacy lower-case-keyed map interface.
     */
    private static synchronized void refreshLegacyMaps() {
        GameData gameData = App.gameData();
        if (mappedGameData == gameData) {
            return;
        }

        SHIPS.clear();
        for (Ship ship : gameData.ships()) {
            SHIPS.put(ship.getName().toLowerCase(Locale.ROOT), ship);
        }
        EVENTS.clear();
        for (Event event : gameData.events()) {
            EVENTS.put(event.getName().toLowerCase(Locale.ROOT), event);
        }
        ASSIGNMENTS.clear();
        for (AdmAssignment assignment : gameData.assignments()) {
            ASSIGNMENTS.put(assignment.getName().toLowerCase(Locale.ROOT), assignment);
        }
        mappedGameData = gameData;
    }
	
	/*/
	private static long getCacheTime() {
		return System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
	}
	//*/

    /**
     * Returns the attached and validated Admirals published by AppBootstrap.
     *
     * @return bootstrapped Admirals
     * @throws IllegalStateException if called before AppBootstrap completes
     */
    public static Admirals getAdmirals() {
        return App.admirals();
    }

    /**
     * Persists Admirals beneath the bootstrapped data directory, logging save failures for the legacy UI.
     *
     * @param admirals current Admirals to persist on application exit
     * @throws IllegalStateException if called before AppBootstrap completes
     */
    public static void setAdmirals(Admirals admirals) {
        try {
            requireAdmiralsStore().save(App.dataDir(), admirals);
        } catch (JAXBException cause) {
            logger.log(Level.WARNING, String.format(ErrorWriting, App.dataDir()), cause);
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
        return requireAdmiralsStore().loadOrCreateFile(file.toPath());
    }

    /**
     * Saves Admirals at an exact legacy file path through AdmiralsStore.
     *
     * @param file     legacy Admirals XML path
     * @param admirals container to persist
     * @throws JAXBException if the XML cannot be written
     */
    public static void saveAdmirals(File file, Admirals admirals) throws JAXBException {
        requireAdmiralsStore().saveFile(file.toPath(), admirals);
    }

    /**
     * Preserves the legacy export facade while delegating file behavior to AdmiralsStore.
     *
     * @param file  target text file
     * @param ships Ships whose display names are exported
     * @return whether the file was written successfully
     */
    public static boolean exportShips(File file, Collection<Ship> ships) {
        return requireAdmiralsStore().exportShipNames(file, ships);
    }

    /**
     * Preserves the legacy import facade while supplying the current GameData to AdmiralsStore.
     *
     * @param file    source text file
     * @param admiral Admiral receiving canonical active Ship names
     * @return recognized line count, or {@code -1} on an I/O failure
     */
    public static int importShips(File file, Admiral admiral) {
        return requireAdmiralsStore().importShipNames(file, App.gameData(), admiral);
    }

    /**
     * Returns the persistence module installed by AppBootstrap.
     *
     * @return initialized AdmiralsStore
     * @throws IllegalStateException if called before bootstrap completes
     */
    private static AdmiralsStore requireAdmiralsStore() {
        AdmiralsStore store = admiralsStore;
        if (store == null) {
            throw new IllegalStateException("AdmiralsStore is unavailable before AppBootstrap completes");
        }
        return store;
    }

}
