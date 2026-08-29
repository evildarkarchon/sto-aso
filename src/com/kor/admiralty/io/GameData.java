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

import static com.kor.admiralty.Globals.FILENAME_ASSIGNMENTS;
import static com.kor.admiralty.Globals.FILENAME_EVENTS;
import static com.kor.admiralty.Globals.FILENAME_RENAMED;
import static com.kor.admiralty.Globals.FILENAME_SHIPCACHE;
import static com.kor.admiralty.Globals.FILENAME_TRAITS;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

import com.kor.admiralty.beans.AdmAssignment;
import com.kor.admiralty.beans.Event;
import com.kor.admiralty.beans.Ship;

/**
 * Owns the application's read-only reference-data structure while preserving its mutable Ship values.
 */
public final class GameData {

    private final SortedMap<String, Ship> shipsByName;
    private final SortedMap<String, String> renamedShips;
    private final SortedMap<String, Event> eventsByName;
    private final SortedMap<String, AdmAssignment> assignmentsByName;

    /**
     * Captures fully loaded maps so callers can never observe an in-progress directory load.
     *
     * @param shipsByName Ships keyed by case-folded current name
     * @param renamedShips case-folded old names mapped to case-folded current names
     * @param eventsByName Events keyed by case-folded name
     * @param assignmentsByName Assignments keyed by case-folded name
     */
    private GameData(
            SortedMap<String, Ship> shipsByName,
            SortedMap<String, String> renamedShips,
            SortedMap<String, Event> eventsByName,
            SortedMap<String, AdmAssignment> assignmentsByName) {
        this.shipsByName = immutableCopy(shipsByName);
        this.renamedShips = immutableCopy(renamedShips);
        this.eventsByName = immutableCopy(eventsByName);
        this.assignmentsByName = immutableCopy(assignmentsByName);
    }

    /**
     * Loads all required reference-data CSVs from a directory, with traits parsed before Ships.
     *
     * @param directory directory containing the five required GameData files
     * @return completely loaded GameData
     * @throws GameDataLoadException if a required file is missing, unreadable, or cannot be parsed
     */
    public static GameData load(Path directory) throws GameDataLoadException {
        Objects.requireNonNull(directory, "directory");

        Path shipsFile = directory.resolve(FILENAME_SHIPCACHE);
        Path renamedFile = directory.resolve(FILENAME_RENAMED);
        Path traitsFile = directory.resolve(FILENAME_TRAITS);
        Path eventsFile = directory.resolve(FILENAME_EVENTS);
        Path assignmentsFile = directory.resolve(FILENAME_ASSIGNMENTS);
        String shipsCsv = readRequiredFile(shipsFile);
        String renamedCsv = readRequiredFile(renamedFile);
        String traitsCsv = readRequiredFile(traitsFile);
        String eventsCsv = readRequiredFile(eventsFile);
        String assignmentsCsv = readRequiredFile(assignmentsFile);

        SortedMap<String, Ship> ships = new TreeMap<String, Ship>();
        SortedMap<String, String> renamed = new TreeMap<String, String>();
        SortedMap<String, String> traits = new TreeMap<String, String>();
        SortedMap<String, Event> events = new TreeMap<String, Event>();
        SortedMap<String, AdmAssignment> assignments = new TreeMap<String, AdmAssignment>();

        try {
            TraitsParser.loadTraits(new StringReader(traitsCsv), traits);
            requireEntries(traitsFile, traits);
            ShipDatabaseParser.loadShipDatabase(new StringReader(shipsCsv), ships, traits);
            requireEntries(shipsFile, ships);
            RenamedShipParser.loadRenamedShips(new StringReader(renamedCsv), renamed);
            requireEntries(renamedFile, renamed);
            EventsParser.loadEvents(new StringReader(eventsCsv), events);
            requireEntries(eventsFile, events);
            AssignmentsParser.loadAssignments(new StringReader(assignmentsCsv), assignments);
            requireEntries(assignmentsFile, assignments);
        } catch (RuntimeException cause) {
            throw new GameDataLoadException(directory, cause);
        }

        return new GameData(ships, normalizeRenamedShips(renamed), events, assignments);
    }

    /**
     * Creates an in-memory builder for tests and other callers that already own reference values.
     *
     * @return an empty GameData builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Reads a required file completely before parsing so mid-read I/O cannot produce partial GameData.
     *
     * @param file required CSV file
     * @return decoded CSV contents
     * @throws GameDataLoadException if the file cannot be read completely
     */
    private static String readRequiredFile(Path file) throws GameDataLoadException {
        try {
            // The bundled CSVs contain legacy bytes; String construction preserves FileReader's replacement behavior.
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException | SecurityException cause) {
            throw new GameDataLoadException(file, cause);
        }
    }

    /**
     * Rejects required files that parsed without producing any reference-data entries.
     *
     * @param file parsed CSV file
     * @param entries values produced by that file's parser
     * @throws GameDataLoadException if the file produced no entries
     */
    private static void requireEntries(Path file, Map<?, ?> entries) throws GameDataLoadException {
        if (entries.isEmpty()) {
            throw new GameDataLoadException(
                    file,
                    new IllegalArgumentException("Required GameData file contains no entries"));
        }
    }

    /**
     * Normalizes both sides of Renamed Ship entries for case-insensitive lookup.
     *
     * @param renamedShips old Ship names mapped to current names
     * @return a new map with both names case-folded
     */
    private static SortedMap<String, String> normalizeRenamedShips(Map<String, String> renamedShips) {
        SortedMap<String, String> normalized = new TreeMap<String, String>();
        for (Map.Entry<String, String> entry : renamedShips.entrySet()) {
            normalized.put(normalize(entry.getKey()), normalize(entry.getValue()));
        }
        return normalized;
    }

    /**
     * Returns a locale-stable Ship-name or trait-name key.
     *
     * @param value name to normalize
     * @return the name case-folded with the root locale
     */
    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    /**
     * Copies and freezes a sorted map without cloning its mutable values.
     *
     * @param values sorted values to copy
     * @return an unmodifiable sorted-map copy
     */
    private static <K, V> SortedMap<K, V> immutableCopy(SortedMap<K, V> values) {
        return Collections.unmodifiableSortedMap(new TreeMap<K, V>(values));
    }

    /**
     * Looks up a Ship case-insensitively and follows one Renamed Ship entry when present.
     *
     * @param name current or old Ship name; null is treated as unknown
     * @return the current mutable Ship object, or null when the name is unknown
     */
    public Ship ship(String name) {
        if (name == null) {
            return null;
        }
        String normalizedName = normalize(name);
        String currentName = renamedShips.get(normalizedName);
        return shipsByName.get(currentName == null ? normalizedName : currentName);
    }

    /**
     * Returns every mutable Ship in case-insensitive name order.
     *
     * @return an unmodifiable collection backed by this GameData instance
     */
    public Collection<Ship> ships() {
        return shipsByName.values();
    }

    /**
     * Returns every Event in case-insensitive name order.
     *
     * @return an unmodifiable collection backed by this GameData instance
     */
    public Collection<Event> events() {
        return eventsByName.values();
    }

    /**
     * Returns every Assignment in case-insensitive name order.
     *
     * @return an unmodifiable collection backed by this GameData instance
     */
    public Collection<AdmAssignment> assignments() {
        return assignmentsByName.values();
    }

    /**
     * Builds GameData from caller-owned Ships, Renamed Ship entries, and Starship Traits.
     */
    public static final class Builder {

        private final List<Ship> ships = new ArrayList<Ship>();
        private final SortedMap<String, String> renamedShips = new TreeMap<String, String>();
        private final SortedMap<String, String> traits = new TreeMap<String, String>();

        private Builder() {
        }

        /**
         * Adds caller-owned mutable Ships without cloning them.
         *
         * @param ships Ships to include
         * @return this builder
         */
        public Builder ships(Collection<? extends Ship> ships) {
            this.ships.addAll(Objects.requireNonNull(ships, "ships"));
            return this;
        }

        /**
         * Adds old-to-current Renamed Ship entries; entries are normalized during build.
         *
         * @param renamedShips old names mapped to current names
         * @return this builder
         */
        public Builder renamedShips(Map<String, String> renamedShips) {
            this.renamedShips.putAll(Objects.requireNonNull(renamedShips, "renamedShips"));
            return this;
        }

        /**
         * Adds Starship Trait names mapped to their resolved descriptions.
         *
         * @param traits trait names mapped to descriptions
         * @return this builder
         */
        public Builder traits(Map<String, String> traits) {
            for (Map.Entry<String, String> entry : Objects.requireNonNull(traits, "traits").entrySet()) {
                this.traits.put(normalize(entry.getKey()), entry.getValue());
            }
            return this;
        }

        /**
         * Resolves supplied trait names and creates structurally read-only GameData.
         *
         * @return GameData containing the same mutable Ship instances supplied to this builder
         */
        public GameData build() {
            SortedMap<String, Ship> shipsByName = new TreeMap<String, Ship>();
            for (Ship ship : ships) {
                ship.setTrait(ShipDatabaseParser.resolveStarshipTrait(ship.getTrait(), traits));
                shipsByName.put(normalize(ship.getName()), ship);
            }
            return new GameData(
                    shipsByName,
                    normalizeRenamedShips(renamedShips),
                    new TreeMap<String, Event>(),
                    new TreeMap<String, AdmAssignment>());
        }
    }
}
