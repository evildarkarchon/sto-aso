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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.Admirals;
import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.RosterState;
import com.kor.admiralty.beans.RosterView;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.enums.PlayerFaction;

/**
 * Persists Admirals and their Ship-name lists without assuming a working
 * directory.
 */
public class AdmiralsStore {

    private final Marshaller marshaller;
    private final Unmarshaller unmarshaller;

    /**
     * Builds the JAXB machinery eagerly so configuration failures surface during
     * application startup.
     *
     * @throws AdmiralsStoreException if the Admirals persistence machinery cannot
     *                                be initialized
     */
    public AdmiralsStore() throws AdmiralsStoreException {
        try {
            JAXBContext context = JAXBContext.newInstance(PersistedAdmirals.class);
            Marshaller configuredMarshaller = context.createMarshaller();
            configuredMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller = configuredMarshaller;
            unmarshaller = context.createUnmarshaller();
        } catch (JAXBException cause) {
            throw new AdmiralsStoreException("Unable to initialize Admirals XML persistence", cause);
        }
    }

    /**
     * Resolves the fixed Admirals filename without consulting process-global
     * filesystem state.
     */
    private static Path admiralsFile(Path directory) {
        return Objects.requireNonNull(directory, "directory").resolve(FILENAME_ADMIRALS);
    }

    /**
     * Restores lookup-ready runtime Admirals while repairing legacy Roster names
     * and reusable state.
     *
     * @param persisted unmarshalled historical XML values
     * @param gameData  reference data used to resolve every saved Ship name
     * @return fully initialized Admirals containing canonical Roster state
     */
    private static Admirals restore(PersistedAdmirals persisted, GameData gameData) {
        List<Admiral> restoredAdmirals = new ArrayList<Admiral>();
        for (PersistedAdmiral persistedAdmiral : persisted.getAdmirals()) {
            List<Ship> maintenance = canonicalReusableShips(persistedAdmiral.getMaintenance(), gameData);
            List<Ship> active = canonicalReusableShips(persistedAdmiral.getActive(), gameData);
            Set<Ship> maintenanceShips = new LinkedHashSet<Ship>(maintenance);
            // A legacy conflict is repaired conservatively so the reusable Ship is not made
            // deployable.
            active.removeIf(maintenanceShips::contains);

            Admiral admiral = Admiral.restore(
                    gameData,
                    persistedAdmiral.getName(),
                    persistedAdmiral.getFaction(),
                    active,
                    maintenance,
                    canonicalShips(persistedAdmiral.getOneTime(), gameData),
                    canonicalUsage(persistedAdmiral.getUsage(), gameData),
                    persistedAdmiral.getPrioritizeActive());
            restoredAdmirals.add(admiral);
        }
        return Admirals.restore(gameData, restoredAdmirals);
    }

    /**
     * Resolves saved reusable Ship names and preserves the first occurrence of each
     * canonical name.
     *
     * @param names    saved Active or Maintenance Ship names
     * @param gameData reference data used for case-insensitive and Renamed Ship
     *                 lookup
     * @return canonical known Ships without duplicates, in first-seen order
     */
    private static List<Ship> canonicalReusableShips(List<String> names, GameData gameData) {
        return new ArrayList<Ship>(new LinkedHashSet<Ship>(canonicalShips(names, gameData)));
    }

    /**
     * Resolves saved Ship names, dropping unknown entries while retaining known
     * multiplicity and order.
     *
     * @param names    saved Ship names
     * @param gameData reference data used for case-insensitive and Renamed Ship
     *                 lookup
     * @return canonical known Ships in saved order
     */
    private static List<Ship> canonicalShips(List<String> names, GameData gameData) {
        List<Ship> canonical = new ArrayList<Ship>();
        for (String name : names) {
            Ship ship = gameData.ship(name);
            if (ship != null) {
                canonical.add(ship);
            }
        }
        return canonical;
    }

    /**
     * Resolves historical usage keys and combines Renamed Ship and case-variant
     * names canonically.
     *
     * @param usage    saved usage counts keyed by current, case-varied, or Renamed
     *                 Ship names
     * @param gameData reference data used to resolve each key
     * @return known usage counts keyed by canonical Ship
     * @throws IllegalArgumentException if a saved count is null or negative
     * @throws ArithmeticException      if combined canonical counts exceed the
     *                                  integer range
     */
    private static Map<Ship, Integer> canonicalUsage(Map<String, Integer> usage, GameData gameData) {
        Map<Ship, Integer> canonical = new HashMap<Ship, Integer>();
        for (Map.Entry<String, Integer> entry : usage.entrySet()) {
            if (entry.getValue() == null || entry.getValue() < 0) {
                throw new IllegalArgumentException("Ship usage counts must be non-negative: " + entry.getKey());
            }
            Ship ship = gameData.ship(entry.getKey());
            if (ship != null) {
                int count = Math.addExact(canonical.getOrDefault(ship, 0), entry.getValue());
                canonical.put(ship, count);
            }
        }
        return canonical;
    }

    /**
     * Copies runtime Admiral state into the private values that define the
     * historical XML contract.
     *
     * @param admirals runtime container to translate without exposing its objects
     *                 to JAXB
     * @return wire values ready for marshalling
     */
    private static PersistedAdmirals persist(Admirals admirals) {
        List<PersistedAdmiral> persistedAdmirals = new ArrayList<PersistedAdmiral>();
        for (Admiral admiral : admirals.getAdmirals()) {
            PersistedAdmiral persistedAdmiral = new PersistedAdmiral();
            persistedAdmiral.setName(admiral.getName());
            persistedAdmiral.setFaction(admiral.getFaction());
            RosterView roster = admiral.getRoster();
            persistedAdmiral.setActive(cardNames(roster.getActiveCardsInRosterOrder()));
            persistedAdmiral.setMaintenance(cardNames(roster.getMaintenanceCardsInRosterOrder()));
            persistedAdmiral.setOneTime(cardNames(roster.getOneTimeCardsInRosterOrder()));
            persistedAdmiral.setUsage(new HashMap<String, Integer>(admiral.getUsageCounts()));
            persistedAdmiral.setPrioritizeActive(admiral.getPrioritizeActive());
            persistedAdmirals.add(persistedAdmiral);
        }

        PersistedAdmirals persisted = new PersistedAdmirals();
        persisted.setAdmirals(persistedAdmirals);
        return persisted;
    }

    /**
     * Projects canonical Ship names from immutable Roster cards for the historical
     * XML representation.
     *
     * @param cards cards in stable Roster order
     * @return mutable names owned by the private JAXB wire value
     */
    private static List<String> cardNames(Collection<RosterCard> cards) {
        List<String> names = new ArrayList<String>(cards.size());
        for (RosterCard card : cards) {
            names.add(card.getShip().getName());
        }
        return names;
    }

    /**
     * Loads and canonically restores Admirals ready for immediate Ship lookup
     * through the supplied GameData.
     *
     * @param directory directory containing the Admirals XML file
     * @param gameData  reference data used to construct and canonicalize restored
     *                  Admirals
     * @return fully initialized persisted Admirals, or one default Admiral when the
     *         file did not exist
     * @throws AdmiralsStoreException if the file cannot be created, read, or
     *                                completely restored
     * @throws NullPointerException   if {@code directory} or {@code gameData} is
     *                                null
     */
    public Admirals loadOrCreate(Path directory, GameData gameData) throws AdmiralsStoreException {
        Objects.requireNonNull(gameData, "gameData");
        Path file = admiralsFile(directory);
        if (Files.notExists(file)) {
            Admirals admirals = new Admirals(gameData);
            save(directory, admirals);
            return admirals;
        }
        try {
            PersistedAdmirals persisted = (PersistedAdmirals) unmarshaller.unmarshal(file.toFile());
            return restore(persisted, gameData);
        } catch (JAXBException | RuntimeException cause) {
            throw new AdmiralsStoreException("Unable to read Admirals XML from " + file, cause);
        }
    }

    /**
     * Saves Admirals to the configured filename in a caller-supplied directory.
     *
     * @param directory directory that receives the Admirals XML file
     * @param admirals  Admirals container to persist
     * @throws AdmiralsStoreException if the container cannot be marshalled
     */
    public void save(Path directory, Admirals admirals) throws AdmiralsStoreException {
        Objects.requireNonNull(admirals, "admirals");
        Path file = admiralsFile(directory);
        try {
            marshaller.marshal(persist(admirals), file.toFile());
        } catch (JAXBException cause) {
            throw new AdmiralsStoreException("Unable to write Admirals XML to " + file, cause);
        }
    }

    /**
     * Exports Ship display names using the legacy text format and platform
     * encoding.
     *
     * @param file  text file that receives one display name per line
     * @param ships Ships to export in collection iteration order
     * @return {@code true} when the complete list was written; {@code false} when
     *         the file could not be opened
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
     * Imports known Ship names into an Admiral, resolving case and renames through
     * GameData.
     * Recognized lines are counted as before even when the Admiral already contains
     * the Ship, then committed once.
     *
     * @param file     text file containing one Ship name per line
     * @param gameData reference data used to resolve each line
     * @param admiral  Admiral that receives canonical active Ship names
     * @return number of recognized lines, or {@code -1} when the file cannot be
     *         read
     */
    public int importShipNames(File file, GameData gameData, Admiral admiral) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(gameData, "gameData");
        Objects.requireNonNull(admiral, "admiral");
        int importedCount = 0;
        List<Ship> importedShips = new ArrayList<Ship>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Ship ship = gameData.ship(line.trim());
                if (ship != null) {
                    importedShips.add(ship);
                    importedCount++;
                }
            }
        } catch (IOException cause) {
            cause.printStackTrace();
            return -1;
        }
        admiral.addReusableShips(importedShips, RosterState.ACTIVE);
        return importedCount;
    }

    /**
     * Private root value that preserves the historical repeated {@code admiral}
     * element representation.
     */
    @XmlRootElement(name = "admirals")
    private static final class PersistedAdmirals {

        private List<PersistedAdmiral> admirals;

        /**
         * Supplies the historical one-Admiral default when an empty root is restored.
         */
        public PersistedAdmirals() {
            admirals = new ArrayList<PersistedAdmiral>();
            admirals.add(new PersistedAdmiral());
        }

        public List<PersistedAdmiral> getAdmirals() {
            return admirals;
        }

        @XmlElement(name = "admiral")
        public void setAdmirals(List<PersistedAdmiral> admirals) {
            this.admirals = admirals;
        }
    }

    /**
     * Private Admiral wire value whose JAXB metadata is the complete historical
     * child ordering contract.
     */
    @XmlType(propOrder = { "name", "faction", "active", "maintenance", "oneTime", "usage" })
    private static final class PersistedAdmiral {

        private String name;
        private PlayerFaction faction;
        private List<String> active;
        private List<String> maintenance;
        private List<String> oneTime;
        private Map<String, Integer> usage;
        private boolean prioritizeActive;

        /**
         * Initializes the same defaults historically supplied by the runtime Admiral
         * constructor.
         */
        public PersistedAdmiral() {
            name = "New Admiral";
            faction = PlayerFaction.Federation;
            active = new ArrayList<String>();
            maintenance = new ArrayList<String>();
            oneTime = new ArrayList<String>();
            usage = new HashMap<String, Integer>();
            prioritizeActive = true;
        }

        public String getName() {
            return name;
        }

        @XmlElement(name = "name", required = true)
        public void setName(String name) {
            this.name = name;
        }

        public PlayerFaction getFaction() {
            return faction;
        }

        @XmlElement(name = "faction", required = true)
        public void setFaction(PlayerFaction faction) {
            this.faction = faction;
        }

        public List<String> getActive() {
            return active;
        }

        @XmlElement(name = "active")
        public void setActive(List<String> active) {
            this.active = active;
        }

        public List<String> getMaintenance() {
            return maintenance;
        }

        @XmlElement(name = "maintenance")
        public void setMaintenance(List<String> maintenance) {
            this.maintenance = maintenance;
        }

        public List<String> getOneTime() {
            return oneTime;
        }

        @XmlElement(name = "onetime")
        public void setOneTime(List<String> oneTime) {
            this.oneTime = oneTime;
        }

        public Map<String, Integer> getUsage() {
            return usage;
        }

        @XmlElement(name = "usage")
        public void setUsage(Map<String, Integer> usage) {
            this.usage = usage;
        }

        public boolean getPrioritizeActive() {
            return prioritizeActive;
        }

        @XmlAttribute(name = "prioritizeActive")
        public void setPrioritizeActive(boolean prioritizeActive) {
            this.prioritizeActive = prioritizeActive;
        }
    }
}
