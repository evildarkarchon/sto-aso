/**
 * Copyright (C) 2015-2019 Dave Kor
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
package com.kor.admiralty.beans;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.kor.admiralty.enums.PlayerFaction;
import com.kor.admiralty.io.GameData;

public class Admirals {

    protected static final String PROP_ADMIRALS = "admirals";

    protected List<Admiral> admirals;
    protected GameData gameData;
    protected PropertyChangeSupport change;

    Admirals() {
        this.admirals = new ArrayList<Admiral>();
        this.admirals.add(new Admiral());
        this.change = new PropertyChangeSupport(this);
    }

    /**
     * Creates a container whose default Admiral is ready for Roster lookups immediately.
     *
     * @param gameData read-only reference data shared by every contained Admiral
     * @throws NullPointerException if {@code gameData} is null
     */
    public Admirals(GameData gameData) {
        this();
        attach(gameData);
    }

    public static Admiral[] toArray(Collection<Admiral> adm) {
        Admiral[] array = new Admiral[adm.size()];
        adm.toArray(array);
        return array;
    }

    /**
     * Attaches shared reference data to this container and every Admiral it currently holds.
     *
     * @param gameData read-only reference data shared by all contained Admirals
     * @throws NullPointerException if {@code gameData} is null
     */
    public void attach(GameData gameData) {
        this.gameData = Objects.requireNonNull(gameData, "gameData");
        for (Admiral admiral : admirals) {
            admiral.attach(gameData);
        }
    }

    /**
     * Returns the current runtime collection; callers should mutate it through this container's operations.
     *
     * @return current Admirals in container order
     */
    public List<Admiral> getAdmirals() {
        return admirals;
    }

    /**
     * Replaces the contained Admirals and attaches them when this container is already attached.
     *
     * @param admirals replacement Admirals in caller-defined order
     */
    public void setAdmirals(List<Admiral> admirals) {
        this.admirals = admirals;
        if (gameData != null) {
            for (Admiral admiral : admirals) {
                admiral.attach(gameData);
            }
        }
        change.firePropertyChange(PROP_ADMIRALS, admirals, admirals);
    }

    public List<Admiral> getFederationAdmirals() {
        return getPlayerFactionAdmirals(PlayerFaction.Federation, PlayerFaction.RomulanFed, PlayerFaction.JemHadarFed);
    }

    public List<Admiral> getKlingonAdmirals() {
        return getPlayerFactionAdmirals(PlayerFaction.Klingon, PlayerFaction.RomulanKDF, PlayerFaction.JemHadarKDF);
    }

    public List<Admiral> getRomulanAdmirals() {
        return getPlayerFactionAdmirals(PlayerFaction.RomulanFed, PlayerFaction.RomulanKDF);
    }

    public List<Admiral> getJemHadarAdmirals() {
        return getPlayerFactionAdmirals(PlayerFaction.JemHadarFed, PlayerFaction.JemHadarKDF);
    }

    protected List<Admiral> getPlayerFactionAdmirals(PlayerFaction... factions) {
        List<Admiral> factionAdmirals = new ArrayList<Admiral>();
        for (Admiral admiral : admirals) {
            for (PlayerFaction faction : factions) {
                if (admiral.getFaction() == faction) {
                    factionAdmirals.add(admiral);
                }
            }
        }
        return factionAdmirals;
    }

    /**
     * Adds an Admiral and gives it this container's GameData when already attached.
     *
     * @param admiral Admiral to add when not already present
     */
    void addAdmiral(Admiral admiral) {
        if (!admirals.contains(admiral)) {
            if (gameData != null) {
                admiral.attach(gameData);
            }
            admirals.add(admiral);
            change.firePropertyChange(PROP_ADMIRALS, admirals, admirals);
        }
    }

    /**
     * Creates, attaches, and adds a new Admiral owned by this container.
     *
     * @return the newly added Admiral with an immediately valid empty Roster
     * @throws IllegalStateException if this container has no GameData
     */
    public Admiral addAdmiral() {
        Admiral admiral = new Admiral(requireGameData());
        addAdmiral(admiral);
        return admiral;
    }

    /**
     * Projects the canonical Ship types present in any current Roster without changing shared GameData values.
     * Reusable cards in either state and every available One-Time type are included; historical usage is not.
     *
     * @return an unmodifiable naturally ordered snapshot of current Roster Ship types
     * @throws IllegalStateException if this container has no GameData
     */
    public Set<Ship> getCurrentRosterShipTypes() {
        requireGameData();
        Set<Ship> shipTypes = new TreeSet<Ship>();
        for (Admiral admiral : admirals) {
            for (RosterCard card : admiral.getRoster().getCards()) {
                shipTypes.add(card.getShip());
            }
        }
        return Collections.unmodifiableSet(shipTypes);
    }

    /**
     * Projects immutable usage rows for the selected Admirals without changing shared GameData Ships.
     * Rows include the union of current reusable and One-Time Roster types with historical usage, in natural Ship order.
     *
     * @param admirals Admirals whose current Rosters and usage history should be combined
     * @return unmodifiable, naturally ordered usage-row snapshot
     * @throws IllegalStateException if this container has no GameData
     * @throws NullPointerException if the array or one of its Admirals is null
     * @throws ArithmeticException if aggregate usage exceeds the integer range
     */
    public List<ShipUsageRow> getShipUsageRows(Admiral... admirals) {
        GameData attachedGameData = requireGameData();
        Objects.requireNonNull(admirals, "admirals");
        Map<Ship, Integer> deploymentCounts = new TreeMap<Ship, Integer>();
        Set<Ship> currentRosterShipTypes = new TreeSet<Ship>();
        for (Admiral admiral : admirals) {
            Objects.requireNonNull(admiral, "admiral");
            for (RosterCard card : admiral.getRoster().getCards()) {
                Ship ship = attachedGameData.ship(card.getShip().getName());
                if (ship != null) {
                    currentRosterShipTypes.add(ship);
                }
            }
            for (Map.Entry<String, Integer> entry : admiral.getUsage().entrySet()) {
                Ship ship = attachedGameData.ship(entry.getKey());
                if (ship != null) {
                    int previousCount = deploymentCounts.getOrDefault(ship, 0);
                    deploymentCounts.put(ship, Math.addExact(previousCount, entry.getValue()));
                }
            }
        }

        Set<Ship> projectedShipTypes = new TreeSet<Ship>(currentRosterShipTypes);
        projectedShipTypes.addAll(deploymentCounts.keySet());
        List<ShipUsageRow> rows = new ArrayList<ShipUsageRow>(projectedShipTypes.size());
        for (Ship ship : projectedShipTypes) {
            rows.add(new ShipUsageRow(
                    ship,
                    deploymentCounts.getOrDefault(ship, 0),
                    currentRosterShipTypes.contains(ship)));
        }
        return Collections.unmodifiableList(rows);
    }

    /**
     * Returns this container's attached reference data for lookup-dependent operations.
     *
     * @return the attached GameData
     * @throws IllegalStateException if this container has not been attached
     */
    private GameData requireGameData() {
        if (gameData == null) {
            throw new IllegalStateException("Admirals must be attached to GameData before resolving Ships");
        }
        return gameData;
    }

    public void removeAdmiral(Admiral admiral) {
        if (admirals.contains(admiral)) {
            admirals.remove(admiral);
            change.firePropertyChange(PROP_ADMIRALS, admirals, admirals);
        }
    }

    public Admiral findByName(String name) {
        for (Admiral admiral : admirals) {
            if (admiral.getName().equalsIgnoreCase(name)) {
                return admiral;
            }
        }
        return null;
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        change.addPropertyChangeListener(l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        change.removePropertyChangeListener(l);
    }

    @Override
    public String toString() {
        return admirals.toString();
    }

}
