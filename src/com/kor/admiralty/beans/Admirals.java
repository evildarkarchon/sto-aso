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

    private static final String PROP_ADMIRALS = "admirals";

    private final List<Admiral> admirals;
    private final GameData gameData;
    private final PropertyChangeSupport change;

    /**
     * Creates a container whose default Admiral is ready for Roster lookups immediately.
     *
     * @param gameData read-only reference data shared by every contained Admiral
     * @throws NullPointerException if {@code gameData} is null
     */
    public Admirals(GameData gameData) {
        this.gameData = Objects.requireNonNull(gameData, "gameData");
        this.admirals = new ArrayList<Admiral>();
        this.admirals.add(new Admiral(gameData));
        this.change = new PropertyChangeSupport(this);
    }

    /**
     * Initializes a restored container from a defensive Admiral list copy, creating one default Admiral when empty.
     *
     * @param gameData         reference data used for subsequently created Admirals and projections
     * @param restoredAdmirals mutable defensive copy in persisted order
     */
    private Admirals(GameData gameData, List<Admiral> restoredAdmirals) {
        this.gameData = gameData;
        this.admirals = restoredAdmirals;
        if (this.admirals.isEmpty()) {
            this.admirals.add(new Admiral(gameData));
        }
        this.change = new PropertyChangeSupport(this);
    }

    /**
     * Restores a container from fully initialized Admirals without a later attachment or validation phase.
     *
     * @param gameData         read-only reference data shared by every restored Admiral
     * @param restoredAdmirals Admirals in persisted order
     * @return a construction-safe container, with one default Admiral when the collection is empty
     * @throws NullPointerException if an argument or Admiral is null
     */
    public static Admirals restore(GameData gameData, Collection<Admiral> restoredAdmirals) {
        Objects.requireNonNull(gameData, "gameData");
        Objects.requireNonNull(restoredAdmirals, "restoredAdmirals");
        List<Admiral> copy = new ArrayList<Admiral>(restoredAdmirals.size());
        for (Admiral admiral : restoredAdmirals) {
            copy.add(Objects.requireNonNull(admiral, "restoredAdmirals contains null"));
        }
        return new Admirals(gameData, copy);
    }

    public static Admiral[] toArray(Collection<Admiral> adm) {
        Admiral[] array = new Admiral[adm.size()];
        adm.toArray(array);
        return array;
    }

    /**
     * Returns the current runtime collection without exposing structural mutation.
     *
     * @return current Admirals in container order
     */
    public List<Admiral> getAdmirals() {
        return Collections.unmodifiableList(new ArrayList<Admiral>(admirals));
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
        return Collections.unmodifiableList(factionAdmirals);
    }

    /**
     * Creates and adds a fully initialized Admiral owned by this container.
     *
     * @return the newly added Admiral with an immediately valid empty Roster
     */
    public Admiral addAdmiral() {
        Admiral admiral = new Admiral(gameData);
        admirals.add(admiral);
        change.firePropertyChange(PROP_ADMIRALS, null, admiral);
        return admiral;
    }

    /**
     * Projects the canonical Ship types present in any current Roster without changing shared GameData values.
     * Reusable cards in either state and every available One-Time type are included; historical usage is not.
     *
     * @return an unmodifiable naturally ordered snapshot of current Roster Ship types
     */
    public Set<Ship> getCurrentRosterShipTypes() {
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
     * @throws NullPointerException if the array or one of its Admirals is null
     * @throws ArithmeticException  if aggregate usage exceeds the integer range
     */
    public List<ShipUsageRow> getShipUsageRows(Admiral... admirals) {
        Objects.requireNonNull(admirals, "admirals");
        Map<Ship, Integer> deploymentCounts = new TreeMap<Ship, Integer>();
        Set<Ship> currentRosterShipTypes = new TreeSet<Ship>();
        for (Admiral admiral : admirals) {
            Objects.requireNonNull(admiral, "admiral");
            for (RosterCard card : admiral.getRoster().getCards()) {
                Ship ship = gameData.ship(card.getShip().getName());
                if (ship != null) {
                    currentRosterShipTypes.add(ship);
                }
            }
            for (Map.Entry<String, Integer> entry : admiral.getUsageCounts().entrySet()) {
                Ship ship = gameData.ship(entry.getKey());
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
