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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

import com.kor.admiralty.Globals;
import com.kor.admiralty.enums.PlayerFaction;
import com.kor.admiralty.enums.ShipViewMode;
import com.kor.admiralty.io.GameData;

public class Admiral {

    public static final String PROP_NAME = "name";
    public static final String PROP_FACTION = "faction";
    public static final String PROP_ACTIVE = "active";
    public static final String PROP_MAINTENANCE = "maintenance";
    public static final String PROP_ONETIME = "oneTime";
    //public static final String PROP_SCHEDULE = "maintenance2";
    public static final String PROP_USAGE = "usage";
    public static final String PROP_ASSIGNMENTCOUNT = "numAssignments";
    public static final String PROP_PRIORITIZEACTIVE = "prioritizeActive";
    public static final String PROP_ASSIGNMENTS = "assignments";

    protected String name;
    protected PlayerFaction faction;
    private List<String> active;
    private List<String> maintenance;
    //protected Map<String, Long> maintenanceV2;
    protected List<String> oneTime;
    protected Map<String, Integer> usage;
    protected int numAssignments;
    protected boolean prioritizeActive;
    protected List<Assignment> assignments;
    protected GameData gameData;
    protected PropertyChangeSupport change;
    private Roster roster;
    private final List<RosterChangeListener> rosterChangeListeners;

    Admiral() {
        this.name = "New Admiral";
        this.faction = PlayerFaction.Federation;
        this.active = new ArrayList<String>();
        this.maintenance = new ArrayList<String>();
        //this.maintenanceV2 = new HashMap<String, Long>();
        this.oneTime = new ArrayList<String>();
        this.usage = new HashMap<String, Integer>();
        this.numAssignments = 1;
        this.prioritizeActive = true;
        this.assignments = new ArrayList<Assignment>();
        this.change = new PropertyChangeSupport(this);
        this.rosterChangeListeners = new ArrayList<RosterChangeListener>();
        for (int i = 0; i < Globals.MAX_ASSIGNMENTS; i++) {
            this.assignments.add(new Assignment());
        }
    }

    /**
     * Creates an Admiral that can resolve its empty Roster through the supplied reference data immediately.
     *
     * @param gameData read-only reference data used by lookup-dependent operations
     * @throws NullPointerException if {@code gameData} is null
     */
    public Admiral(GameData gameData) {
        this();
        attach(gameData);
    }

    /**
     * Attaches the reference data used to resolve the Admiral's persisted Ship names.
     *
     * @param gameData read-only reference data shared by the containing Admirals object
     * @throws NullPointerException if {@code gameData} is null
     */
    public void attach(GameData gameData) {
        this.gameData = Objects.requireNonNull(gameData, "gameData");
        roster = Roster.restore(gameData, active, maintenance, oneTime);
        active = new ArrayList<String>(roster.names(RosterState.ACTIVE));
        maintenance = new ArrayList<String>(roster.names(RosterState.MAINTENANCE));
        oneTime = new ArrayList<String>(roster.oneTimeNames());
    }

    /**
     * Returns one immutable snapshot containing reusable and One-Time Roster state at a single planning revision.
     * Mutation and listener delivery are caller-thread-confined, normally to the Swing event thread.
     *
     * @return the current complete Roster view
     * @throws IllegalStateException if GameData has not been attached
     */
    public RosterView getRoster() {
        requireGameData();
        return roster.view();
    }

    /**
     * Adds reusable Ships to a destination, atomically moving any card already in the other reusable state.
     * A same-destination add is a no-op and does not advance the Roster revision.
     *
     * @param ships Ships to resolve canonically through this Admiral's GameData
     * @param destination Active or Maintenance
     * @throws IllegalArgumentException if a Ship is unknown or the destination is Absent
     * @throws NullPointerException if an argument or collection element is null
     * @throws IllegalStateException if GameData has not been attached
     */
    public void addReusableShips(Collection<Ship> ships, RosterState destination) {
        requireGameData();
        mutateRoster(() -> roster.addReusableShips(ships, destination));
    }

    /**
     * Moves reusable cards identified by a prior immutable Roster view in one atomic operation.
     *
     * @param cards cards belonging to this Admiral, from any still-applicable view
     * @param destination Active or Maintenance
     * @throws IllegalArgumentException if a card is foreign or removed, or the destination is Absent
     * @throws NullPointerException if an argument or collection element is null
     * @throws IllegalStateException if GameData has not been attached
     */
    public void moveReusableCards(Collection<RosterCard> cards, RosterState destination) {
        requireGameData();
        mutateRoster(() -> roster.moveReusableCards(cards, destination));
    }

    /**
     * Removes reusable cards identified by a prior immutable Roster view in one atomic operation.
     *
     * @param cards cards belonging to this Admiral, from any still-applicable view
     * @throws IllegalArgumentException if a card is foreign or was already removed
     * @throws NullPointerException if {@code cards} or one of its elements is null
     * @throws IllegalStateException if GameData has not been attached
     */
    public void removeReusableCards(Collection<RosterCard> cards) {
        requireGameData();
        mutateRoster(() -> roster.removeReusableCards(cards));
    }

    /**
     * Adjusts one One-Time Ship quantity while preserving the identities of copies that remain available.
     * Quantity zero removes that One-Time Ship type from the Roster.
     *
     * @param ship Ship to resolve canonically through this Admiral's GameData
     * @param adjustment signed quantity change
     * @throws IllegalArgumentException if the Ship is unknown or the resulting quantity would be negative
     * @throws ArithmeticException if the resulting quantity exceeds the integer range
     * @throws NullPointerException if {@code ship} is null
     * @throws IllegalStateException if GameData has not been attached
     */
    public void adjustOneTimeShipQuantity(Ship ship, int adjustment) {
        requireGameData();
        mutateRoster(() -> roster.adjustOneTimeShipQuantity(ship, adjustment));
    }

    /**
     * Registers a caller-thread listener for committed reusable and One-Time Roster changes.
     *
     * @param listener listener to notify after each successful operation
     * @throws NullPointerException if {@code listener} is null
     */
    public void addRosterChangeListener(RosterChangeListener listener) {
        rosterChangeListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Stops a previously registered Roster listener from receiving future changes.
     *
     * @param listener listener to remove
     */
    public void removeRosterChangeListener(RosterChangeListener listener) {
        rosterChangeListeners.remove(listener);
    }

    /**
     * Captures legacy property values around one Roster transaction and publishes only a committed change.
     *
     * @param mutation deferred Roster operation, returning null for a no-op
     * @throws RuntimeException if validation or commit fails; no listeners are notified in that case
     */
    private void mutateRoster(Supplier<RosterChange> mutation) {
        List<String> oldActive = new ArrayList<String>(active);
        List<String> oldMaintenance = new ArrayList<String>(maintenance);
        List<String> oldOneTime = new ArrayList<String>(oneTime);
        RosterChange rosterChange = mutation.get();
        publishRosterChange(rosterChange, oldActive, oldMaintenance, oldOneTime);
    }

    /**
     * Synchronizes temporary list compatibility values and notifies every listener after the Roster commit.
     *
     * @param rosterChange committed change, or null when the mutation was a no-op
     * @param oldActive Active compatibility names captured before the operation
     * @param oldMaintenance Maintenance compatibility names captured before the operation
     * @param oldOneTime expanded One-Time compatibility names captured before the operation
     */
    private void publishRosterChange(
            RosterChange rosterChange,
            List<String> oldActive,
            List<String> oldMaintenance,
            List<String> oldOneTime) {
        if (rosterChange == null) {
            return;
        }
        active = new ArrayList<String>(roster.names(RosterState.ACTIVE));
        maintenance = new ArrayList<String>(roster.names(RosterState.MAINTENANCE));
        oneTime = new ArrayList<String>(roster.oneTimeNames());

        // A snapshot permits listeners to add or remove subscriptions safely during synchronous notification.
        for (RosterChangeListener listener : new ArrayList<RosterChangeListener>(rosterChangeListeners)) {
            listener.rosterChanged(rosterChange);
        }
        change.firePropertyChange(PROP_ACTIVE, oldActive, active);
        change.firePropertyChange(PROP_MAINTENANCE, oldMaintenance, maintenance);
        change.firePropertyChange(PROP_ONETIME, oldOneTime, oneTime);
    }

    /**
     * Resolves temporary persisted names and replaces one reusable state in a single operation.
     *
     * @param names persisted or canonical Ship names; unknown names are dropped
     * @param destination Active or Maintenance
     * @throws NullPointerException if {@code names} or one of its elements is null
     */
    private void replaceReusableNames(Collection<String> names, RosterState destination) {
        List<Ship> canonicalShips = new ArrayList<Ship>();
        for (String name : names) {
            Objects.requireNonNull(name, "names contains null");
            Ship ship = gameData.ship(name);
            if (ship != null) {
                canonicalShips.add(ship);
            }
        }
        replaceReusableShips(canonicalShips, destination);
    }

    /**
     * Replaces one reusable state through the internal Roster and publishes its committed change.
     *
     * @param ships complete replacement membership for the destination
     * @param destination Active or Maintenance
     * @throws IllegalArgumentException if a Ship is unknown or the destination is Absent
     * @throws NullPointerException if an argument or collection element is null
     * @throws IllegalStateException if GameData has not been attached
     */
    private void replaceReusableShips(Collection<Ship> ships, RosterState destination) {
        requireGameData();
        mutateRoster(() -> roster.replaceReusableShips(ships, destination));
    }

    /**
     * Resolves repeated compatibility names and replaces all One-Time quantities in one Roster commit.
     *
     * @param names persisted or canonical One-Time names; unknown names are dropped
     * @throws NullPointerException if {@code names} or one of its elements is null
     */
    private void replaceOneTimeNames(Collection<String> names) {
        List<Ship> canonicalShips = new ArrayList<Ship>();
        for (String name : names) {
            Objects.requireNonNull(name, "names contains null");
            Ship ship = gameData.ship(name);
            if (ship != null) {
                canonicalShips.add(ship);
            }
        }
        mutateRoster(() -> roster.replaceOneTimeShips(canonicalShips));
    }

    /**
     * Resolves one compatibility name and adds the known canonical Ship to a reusable state.
     *
     * @param shipName current, case-varied, or renamed Ship name
     * @param destination Active or Maintenance
     * @throws NullPointerException if {@code shipName} is null
     */
    private void addReusableName(String shipName, RosterState destination) {
        Objects.requireNonNull(shipName, "shipName");
        Ship ship = gameData.ship(shipName);
        if (ship != null) {
            addReusableShips(List.of(ship), destination);
        }
    }

    /**
     * Resolves one compatibility name and removes its card only when it is in the expected state.
     *
     * @param shipName current, case-varied, or renamed Ship name
     * @param expectedState state from which the card may be removed
     * @throws NullPointerException if {@code shipName} is null
     */
    private void removeReusableName(String shipName, RosterState expectedState) {
        Objects.requireNonNull(shipName, "shipName");
        Ship ship = gameData.ship(shipName);
        if (ship != null) {
            removeReusableShips(List.of(ship), expectedState);
        }
    }

    /**
     * Resolves Ship-shaped compatibility values and removes their current identity-bearing cards atomically.
     *
     * @param ships Ship-shaped values to resolve; unknown Ships are ignored
     * @param expectedState required current state, or null to remove from either present state
     * @throws NullPointerException if {@code ships} or one of its elements is null
     * @throws IllegalStateException if GameData has not been attached
     */
    private void removeReusableShips(Collection<Ship> ships, RosterState expectedState) {
        requireGameData();
        Objects.requireNonNull(ships, "ships");
        Set<String> canonicalNames = new LinkedHashSet<String>();
        for (Ship ship : ships) {
            Objects.requireNonNull(ship, "ships contains null");
            Ship canonicalShip = gameData.ship(ship.getName());
            if (canonicalShip != null) {
                canonicalNames.add(canonicalShip.getName());
            }
        }

        List<RosterCard> matchingCards = new ArrayList<RosterCard>();
        for (RosterCard card : roster.view().getReusableCards()) {
            if (canonicalNames.contains(card.getShip().getName())
                    && (expectedState == null || card.getState() == expectedState)) {
                matchingCards.add(card);
            }
        }
        removeReusableCards(matchingCards);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        String oldName = this.name;
        this.name = name;
        change.firePropertyChange(PROP_NAME, oldName, this.name);
    }

    public PlayerFaction getFaction() {
        return faction;
    }

    public void setFaction(PlayerFaction faction) {
        PlayerFaction oldFaction = this.faction;
        this.faction = faction;
        change.firePropertyChange(PROP_FACTION, oldFaction, this.faction);
    }

    /**
     * Returns a temporary persistence-compatible snapshot of canonical Active Ship names.
     *
     * @return unmodifiable names in historical XML order
     */
    public List<String> getActive() {
        return Collections.unmodifiableList(new ArrayList<String>(active));
    }

    /**
     * Replaces Active reusable Ships through the atomic Roster compatibility path.
     * Before attachment, names are retained for restoration; after attachment, unknown names are dropped.
     *
     * @param active persisted or canonical Active Ship names
     * @throws NullPointerException if {@code active} or one of its names is null
     */
    public void setActive(List<String> active) {
        requireNames(active, "active");
        if (roster == null) {
            ArrayList<String> oldList = new ArrayList<String>(this.active);
            this.active = new ArrayList<String>(active);
            change.firePropertyChange(PROP_ACTIVE, oldList, this.active);
            return;
        }
        replaceReusableNames(active, RosterState.ACTIVE);
    }

    /**
     * Returns a temporary persistence-compatible snapshot of canonical Maintenance Ship names.
     *
     * @return unmodifiable names in historical XML order
     */
    public List<String> getMaintenance() {
        return Collections.unmodifiableList(new ArrayList<String>(maintenance));
    }

    /**
     * Replaces Maintenance reusable Ships through the atomic Roster compatibility path.
     * Before attachment, names are retained for restoration; after attachment, conflicting Active cards move
     * to Maintenance in the same commit and unknown names are dropped.
     *
     * @param maintenance persisted or canonical Maintenance Ship names
     * @throws NullPointerException if {@code maintenance} or one of its names is null
     */
    public void setMaintenance(List<String> maintenance) {
        requireNames(maintenance, "maintenance");
        if (roster == null) {
            ArrayList<String> oldList = new ArrayList<String>(this.maintenance);
            this.maintenance = new ArrayList<String>(maintenance);
            change.firePropertyChange(PROP_MAINTENANCE, oldList, this.maintenance);
            return;
        }
        replaceReusableNames(maintenance, RosterState.MAINTENANCE);
    }

    /**
     * Returns a temporary persistence-compatible expansion of canonical One-Time quantities.
     *
     * @return unmodifiable repeated names in historical XML order
     */
    public List<String> getOneTime() {
        return Collections.unmodifiableList(new ArrayList<String>(oneTime));
    }

    /**
     * Replaces One-Time quantities from repeated names through the atomic Roster compatibility path.
     * Before attachment, names are retained for restoration; after attachment, unknown names are dropped.
     *
     * @param oneTime persisted or canonical repeated One-Time Ship names
     * @throws NullPointerException if {@code oneTime} or one of its names is null
     */
    public void setOneTime(List<String> oneTime) {
        requireNames(oneTime, "oneTime");
        if (roster == null) {
            ArrayList<String> oldList = new ArrayList<String>(this.oneTime);
            this.oneTime = new ArrayList<String>(oneTime);
            change.firePropertyChange(PROP_ONETIME, oldList, this.oneTime);
            return;
        }
        replaceOneTimeNames(oneTime);
    }

    public Map<String, Integer> getUsage() {
        return usage;
    }
	
	/*
	public void setMaintenanceV2(Map<String, Long> maintenanceV2) {
		Map<String, Long> oldMaintenanceV2 = new HashMap<String, Long>(this.maintenanceV2);
		this.maintenanceV2 = maintenanceV2;
		change.firePropertyChange(PROP_SCHEDULE, oldMaintenanceV2, maintenanceV2);
	}
	
	public Map<String, Long> getMaintenanceV2() {
		return maintenanceV2;
	}
	*/

    public void setUsage(Map<String, Integer> usage) {
        HashMap<String, Integer> oldMap = new HashMap<String, Integer>(this.usage);
        this.usage = usage;
        change.firePropertyChange(PROP_USAGE, oldMap, this.usage);
    }

    public void clearUsage() {
        this.usage.clear();
    }

    public int getAssignmentCount() {
        return numAssignments;
    }

    public void setAssignmentCount(int numAssignments) {
        int oldNum = this.numAssignments;
        this.numAssignments = numAssignments;
        change.firePropertyChange(PROP_ASSIGNMENTCOUNT, oldNum, this.numAssignments);
    }

    public boolean getPrioritizeActive() {
        return prioritizeActive;
    }

    public void setPrioritizeActive(boolean prioritizeActive) {
        boolean oldVal = this.prioritizeActive;
        this.prioritizeActive = prioritizeActive;
        change.firePropertyChange(PROP_PRIORITIZEACTIVE, oldVal, this.prioritizeActive);
    }

    public List<Assignment> getAssignments() {
        return assignments;
    }

    public void setAssignments(List<Assignment> assignments) {
        ArrayList<Assignment> oldList = new ArrayList<Assignment>(this.assignments);
        this.assignments = assignments;
        change.firePropertyChange(PROP_ASSIGNMENTS, oldList, this.assignments);
    }

    /**
     * Adds one known reusable Ship to Active, atomically moving it from Maintenance when necessary.
     * Before attachment the raw name is retained for restoration; after attachment an unknown name is a no-op.
     *
     * @param shipName current, case-varied, or Renamed Ship name
     * @throws NullPointerException if {@code shipName} is null
     */
    public void addActive(String shipName) {
        Objects.requireNonNull(shipName, "shipName");
        if (roster != null) {
            addReusableName(shipName, RosterState.ACTIVE);
            return;
        }
        if (!active.contains(shipName)) {
            List<String> oldActive = new ArrayList<String>(active);
            active.add(shipName);
            change.firePropertyChange(PROP_ACTIVE, oldActive, active);
        }
    }

    /**
     * Removes one known reusable card only when it is currently Active.
     * Before attachment the raw name is removed directly; after attachment an unknown name is a no-op.
     *
     * @param shipName current, case-varied, or Renamed Ship name
     * @throws NullPointerException if {@code shipName} is null
     */
    public void removeActive(String shipName) {
        Objects.requireNonNull(shipName, "shipName");
        if (roster != null) {
            removeReusableName(shipName, RosterState.ACTIVE);
            return;
        }
        if (active.contains(shipName)) {
            List<String> oldActive = new ArrayList<String>(active);
            active.remove(shipName);
            change.firePropertyChange(PROP_ACTIVE, oldActive, active);
        }
    }

    /**
     * Adds one known reusable Ship to Maintenance, atomically moving it from Active when necessary.
     * Before attachment the raw name is retained for restoration; after attachment an unknown name is a no-op.
     *
     * @param shipName current, case-varied, or Renamed Ship name
     * @throws NullPointerException if {@code shipName} is null
     */
    public void addMaintenance(String shipName) {
        Objects.requireNonNull(shipName, "shipName");
        if (roster != null) {
            addReusableName(shipName, RosterState.MAINTENANCE);
            return;
        }
        if (!maintenance.contains(shipName)) {
            List<String> oldMaintenance = new ArrayList<String>(maintenance);
            maintenance.add(shipName);
            change.firePropertyChange(PROP_MAINTENANCE, oldMaintenance, maintenance);
        }
    }

    /**
     * Removes one known reusable card only when it is currently in Maintenance.
     * Before attachment the raw name is removed directly; after attachment an unknown name is a no-op.
     *
     * @param shipName current, case-varied, or Renamed Ship name
     * @throws NullPointerException if {@code shipName} is null
     */
    public void removeMaintenance(String shipName) {
        Objects.requireNonNull(shipName, "shipName");
        if (roster != null) {
            removeReusableName(shipName, RosterState.MAINTENANCE);
            return;
        }
        if (maintenance.contains(shipName)) {
            List<String> oldMaintenance = new ArrayList<String>(maintenance);
            maintenance.remove(shipName);
            change.firePropertyChange(PROP_MAINTENANCE, oldMaintenance, maintenance);
        }
    }

    /**
     * Adds one One-Time copy through the quantity-backed Roster compatibility path.
     *
     * @param shipName current, case-varied, or Renamed Ship name
     * @throws NullPointerException if {@code shipName} is null
     */
    public void addOneTime(String shipName) {
        Objects.requireNonNull(shipName, "shipName");
        if (roster != null) {
            Ship ship = gameData.ship(shipName);
            if (ship != null) {
                adjustOneTimeShipQuantity(ship, 1);
            }
            return;
        }
        List<String> oldOneTime = new ArrayList<String>(oneTime);
        oneTime.add(shipName);
        change.firePropertyChange(PROP_ONETIME, oldOneTime, oneTime);
    }

    /**
     * Removes one available One-Time copy while retaining the legacy absent-name no-op behavior.
     *
     * @param shipName current, case-varied, or Renamed Ship name
     * @throws NullPointerException if {@code shipName} is null
     */
    public void removeOneTime(String shipName) {
        Objects.requireNonNull(shipName, "shipName");
        if (roster != null) {
            Ship ship = gameData.ship(shipName);
            if (ship != null && getRoster().getOneTimeQuantity(ship) > 0) {
                adjustOneTimeShipQuantity(ship, -1);
            }
            return;
        }
        if (!oneTime.contains(shipName)) {
            return;
        }
        List<String> oldOneTime = new ArrayList<String>(oneTime);
        oneTime.remove(shipName);
        change.firePropertyChange(PROP_ONETIME, oldOneTime, oneTime);
    }

    public Assignment getAssignment(int index) {
        if (index < 0) return null;
        if (index >= assignments.size()) return null;
        return assignments.get(index);
    }

    /**
     * Returns the temporary Ship-shaped Active view used by current Swing callers.
     *
     * @return naturally ordered canonical Active Ships
     * @throws IllegalStateException if GameData has not been attached
     */
    public Set<Ship> getActiveShips() {
        Set<Ship> ships = new TreeSet<Ship>();
        _getShips(active, ships);
        return ships;
    }

    /**
     * Replaces Active reusable cards in one atomic compatibility operation.
     *
     * @param ships Ships to resolve through this Admiral's GameData
     * @throws IllegalArgumentException if a Ship is unknown
     * @throws NullPointerException if {@code ships} or one of its elements is null
     * @throws IllegalStateException if GameData has not been attached
     */
    public void setActiveShips(Set<Ship> ships) {
        replaceReusableShips(ships, RosterState.ACTIVE);
    }

    /**
     * Adds reusable Ships to Active in one atomic compatibility operation.
     *
     * @param ships Ships to resolve through this Admiral's GameData
     * @throws IllegalArgumentException if a Ship is unknown
     * @throws NullPointerException if {@code ships} or one of its elements is null
     * @throws IllegalStateException if GameData has not been attached
     */
    public void addActiveShips(Collection<Ship> ships) {
        addReusableShips(ships, RosterState.ACTIVE);
    }

    /**
     * Removes the supplied reusable Ships only when they are currently Active.
     *
     * @param ships Ships to resolve through this Admiral's GameData; unknown Ships are ignored
     * @throws NullPointerException if {@code ships} or one of its elements is null
     * @throws IllegalStateException if GameData has not been attached
     */
    public void removeActiveShips(Collection<Ship> ships) {
        removeReusableShips(ships, RosterState.ACTIVE);
    }

    /**
     * Returns the temporary Ship-shaped Maintenance view used by current Swing callers.
     *
     * @return naturally ordered canonical Maintenance Ships
     * @throws IllegalStateException if GameData has not been attached
     */
    public Set<Ship> getMaintenanceShips() {
        Set<Ship> ships = new TreeSet<Ship>();
        _getShips(maintenance, ships);
        //_getShips(schedule.keySet(), ships);
        return ships;
    }

    /**
     * Replaces Maintenance reusable cards in one atomic compatibility operation.
     *
     * @param ships Ships to resolve through this Admiral's GameData
     * @throws IllegalArgumentException if a Ship is unknown
     * @throws NullPointerException if {@code ships} or one of its elements is null
     * @throws IllegalStateException if GameData has not been attached
     */
    public void setMaintenanceShips(Set<Ship> ships) {
        replaceReusableShips(ships, RosterState.MAINTENANCE);
    }

    /**
     * Adds reusable Ships to Maintenance in one atomic compatibility operation.
     *
     * @param ships Ships to resolve through this Admiral's GameData
     * @throws IllegalArgumentException if a Ship is unknown
     * @throws NullPointerException if {@code ships} or one of its elements is null
     * @throws IllegalStateException if GameData has not been attached
     */
    public void addMaintenanceShips(Collection<Ship> ships) {
        addReusableShips(ships, RosterState.MAINTENANCE);
    }

    /**
     * Removes the supplied reusable Ships only when they are currently in Maintenance.
     *
     * @param ships Ships to resolve through this Admiral's GameData; unknown Ships are ignored
     * @throws NullPointerException if {@code ships} or one of its elements is null
     * @throws IllegalStateException if GameData has not been attached
     */
    public void removeMaintenanceShips(Collection<Ship> ships) {
        removeReusableShips(ships, RosterState.MAINTENANCE);
    }

    /**
     * Removes supplied reusable cards from either present state in one atomic compatibility operation.
     *
     * @param ships Ships to resolve through this Admiral's GameData; unknown Ships are ignored
     * @throws NullPointerException if {@code ships} or one of its elements is null
     * @throws IllegalStateException if GameData has not been attached
     */
    public void removeActiveOrMaintenanceShips(Collection<Ship> ships) {
        removeReusableShips(ships, null);
    }

    /**
     * Returns temporary Ship-shaped adapters for every available One-Time copy.
     * Runtime copy identity remains on the corresponding Roster cards.
     *
     * @return naturally ordered One-Time Ship adapters, including repeated copies
     * @throws IllegalStateException if GameData has not been attached
     */
    public List<Ship> getOneTimeShips() {
        List<Ship> ships = new ArrayList<Ship>();
        for (RosterCard card : getRoster().getOneTimeCards()) {
            ships.add(card.getShip().getOneTimeShip());
        }
        return ships;
    }

    /**
     * Replaces One-Time quantities with one copy of each supplied Ship through the Roster.
     *
     * @param ships complete set of One-Time Ship types
     * @throws NullPointerException if {@code ships} or one of its elements is null
     * @throws IllegalStateException if GameData has not been attached
     */
    public void setOneTimeShips(Set<Ship> ships) {
        requireGameData();
        mutateRoster(() -> roster.replaceOneTimeShips(knownCanonicalShips(ships)));
    }

    /**
     * Adds one One-Time copy for every supplied Ship occurrence in one Roster commit.
     *
     * @param ships Ship-shaped compatibility values; unknown Ships are ignored
     * @throws NullPointerException if {@code ships} or one of its elements is null
     * @throws IllegalStateException if GameData has not been attached
     */
    public void addOneTimeShips(Collection<Ship> ships) {
        requireGameData();
        mutateRoster(() -> roster.adjustOneTimeShipQuantities(knownCanonicalShips(ships), 1));
    }

    /**
     * Removes at most one available One-Time copy for every supplied Ship occurrence in one Roster commit.
     * Unknown or already absent Ships retain the legacy no-op behavior.
     *
     * @param ships Ship-shaped compatibility values to remove
     * @throws NullPointerException if {@code ships} or one of its elements is null
     * @throws IllegalStateException if GameData has not been attached
     */
    public void removeOneTimeShips(Collection<Ship> ships) {
        requireGameData();
        List<Ship> availableShips = availableOneTimeShips(knownCanonicalShips(ships));
        mutateRoster(() -> roster.adjustOneTimeShipQuantities(availableShips, -1));
    }

    /**
     * Retains requested One-Time occurrences only while this Roster still has an available copy.
     * The local remaining counts prevent repeated compatibility values from over-consuming a quantity.
     *
     * @param ships requested One-Time Ship occurrences
     * @return occurrences that can currently be removed or assigned, in request order
     * @throws NullPointerException if {@code ships} or one of its elements is null
     */
    private List<Ship> availableOneTimeShips(Collection<Ship> ships) {
        Objects.requireNonNull(ships, "ships");
        RosterView currentRoster = getRoster();
        Map<String, Integer> remainingQuantities = new HashMap<String, Integer>();
        List<Ship> availableShips = new ArrayList<Ship>();
        for (Ship ship : ships) {
            Objects.requireNonNull(ship, "ships contains null");
            String shipName = ship.getName();
            int remaining = remainingQuantities.computeIfAbsent(
                    shipName,
                    ignored -> currentRoster.getOneTimeQuantity(ship));
            if (remaining > 0) {
                availableShips.add(ship);
                remainingQuantities.put(shipName, remaining - 1);
            }
        }
        return availableShips;
    }

    /**
     * Resolves Ship-shaped compatibility values through GameData while retaining repeated occurrences.
     *
     * @param ships Ship-shaped values to canonicalize; unknown Ships are ignored
     * @return canonical known Ships in input order and multiplicity
     * @throws NullPointerException if {@code ships} or one of its elements is null
     */
    private List<Ship> knownCanonicalShips(Collection<Ship> ships) {
        Objects.requireNonNull(ships, "ships");
        List<Ship> canonicalShips = new ArrayList<Ship>();
        for (Ship ship : ships) {
            Objects.requireNonNull(ship, "ships contains null");
            Ship canonicalShip = gameData.ship(ship.getName());
            if (canonicalShip != null) {
                canonicalShips.add(canonicalShip);
            }
        }
        return canonicalShips;
    }

    public List<Ship> getStarshipTraits() {
        List<Ship> ships = new ArrayList<Ship>();
        _getShips(active, ships, ShipViewMode.StarshipTrait);
        _getShips(maintenance, ships, ShipViewMode.StarshipTrait);
        Collections.sort(ships);
        return ships;
    }

    /**
     * Applies the legacy Ship-shaped deployment behavior while routing reusable moves and One-Time consumption
     * through the authoritative Roster. Exact selected-card validation and structured outcomes remain deferred
     * to the dedicated identity-bearing deployment phase.
     * Null elements and Ships that are neither Active nor currently held One-Time inputs are ignored.
     *
     * @param ships legacy selected Ships to deploy
     * @return the historical Swing-formatted assignment summary
     * @throws NullPointerException if {@code ships} is null
     * @throws IllegalStateException if GameData has not been attached
     */
    public String assignShips(List<Ship> ships) {
        Objects.requireNonNull(ships, "ships");
        StringBuilder sbMaintenance = new StringBuilder();
        StringBuilder sbOneTime = new StringBuilder();
        Map<String, Integer> oldUsage = new HashMap<String, Integer>(usage);
        Map<String, RosterCard> activeCardsByName = new HashMap<String, RosterCard>();
        for (RosterCard card : getRoster().getActiveCards()) {
            activeCardsByName.put(card.getShip().getName(), card);
        }
        List<RosterCard> assignedReusableCards = new ArrayList<RosterCard>();
        List<Ship> requestedOneTimeShips = new ArrayList<Ship>();
        for (Ship ship : ships) {
            if (ship == null) continue;
            String shipName = ship.getName();
            RosterCard activeCard = activeCardsByName.remove(shipName);
            if (activeCard != null) {
                // Move active ship to maintenance roster
                assignedReusableCards.add(activeCard);
                sbMaintenance.append("<li>").append(shipName).append("</li>");
                useShip(shipName);
            } else {
                requestedOneTimeShips.add(ship);
            }
        }
        List<Ship> assignedOneTimeShips = availableOneTimeShips(requestedOneTimeShips);
        for (Ship assignedOneTimeShip : assignedOneTimeShips) {
            String shipName = assignedOneTimeShip.getName();
            sbOneTime.append("<li>").append(shipName).append("</li>");
            useShip(shipName);
        }
        moveReusableCards(assignedReusableCards, RosterState.MAINTENANCE);
        mutateRoster(() -> roster.adjustOneTimeShipQuantities(assignedOneTimeShips, -1));
        change.firePropertyChange(PROP_USAGE, oldUsage, usage);

        String strMaintenance = sbMaintenance.toString();
        String strOneTime = sbOneTime.toString();
        if (strMaintenance.length() + strOneTime.length() == 0) {
            return "These ships have already been assigned.";
        } else {
            StringBuilder sb = new StringBuilder().append("<html>");
            if (strMaintenance.length() > 0) {
                sb.append("Active ship(s) assigned:</br><ul class=\"info\">").append(strMaintenance).append("</ul>");
            }
            if (strOneTime.length() > 0) {
                sb.append("One-time ship(s) assigned:</br><ul class=\"info\">").append(strOneTime).append("</ul>");
            }
            return sb.append("</html>").toString();
        }
    }
	
	/*
	public String assignShipsV2(Map<Ship, Long> ships) {
		StringBuilder sbMaintenance = new StringBuilder();
		StringBuilder sbOneTime = new StringBuilder();
		List<String> oldActive = new ArrayList<String>(active);
		Map<String, Long> oldMaintenanceV2 = new HashMap<String, Long>(maintenanceV2);
		List<String> oldOneTime = new ArrayList<String>(oneTime);
		Map<String, Integer> oldUsage = new HashMap<String, Integer>(usage);
		for (Map.Entry<Ship, Long> entry: ships.entrySet()) {
			Ship ship = entry.getKey();
			long time = entry.getValue();
			if (ship == null) continue;
			String shipName = ship.getName();
			
			if (active.remove(shipName)) {
				// Move active ship to maintenance schedule
				maintenanceV2.put(shipName, time);
				sbMaintenance.append("<li>").append(shipName).append("</li>");
				useShip(shipName);
			}
			else if (oneTime.remove(shipName)) {
				// Removed one-time ship
				sbOneTime.append("<li>").append(shipName).append("</li>");
				useShip(shipName);
			}
		}
		change.firePropertyChange(PROP_ACTIVE, oldActive, active);
		change.firePropertyChange(PROP_SCHEDULE, oldMaintenanceV2, maintenanceV2);
		change.firePropertyChange(PROP_ONETIME, oldOneTime, oneTime);
		change.firePropertyChange(PROP_USAGE, oldUsage, usage);
		
		String strMaintenance = sbMaintenance.toString();
		String strOneTime = sbOneTime.toString();
		if (strMaintenance.length() + strOneTime.length() == 0) {
			return "These ships have already been assigned.";
		}
		else {
			StringBuilder sb = new StringBuilder().append("<html>");
			if (strMaintenance.length() > 0) {
				sb.append("Active ship(s) assigned:</br><ul>").append(strMaintenance).append("</ul>");
			}
			if (strOneTime.length() > 0) {
				sb.append("One-time ship(s) assigned:</br><ul>").append(strOneTime).append("</ul>");
			}
			return sb.append("</html>").toString();
		}
	}
	*/

    public List<Ship> getDeployableShips() {
        List<Ship> ships = new ArrayList<Ship>();
        if (prioritizeActive) {
            ships.addAll(getActiveShips());
            ships.addAll(getOneTimeShips());
        } else {
            ships.addAll(getOneTimeShips());
            ships.addAll(getActiveShips());
        }
        return ships;
    }

    public List<CompositeSolution> solveAssignments(List<Ship> ships) {
        Assignment assignment1 = numAssignments >= 1 ? assignments.get(0) : null;
        Assignment assignment2 = numAssignments >= 2 ? assignments.get(1) : null;
        Assignment assignment3 = numAssignments >= 3 ? assignments.get(2) : null;
        List<CompositeSolution> solutions = Solver.solve(assignment1, assignment2, assignment3, ships, Globals.SOLVER_DEPTH);
        return solutions;
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        change.addPropertyChangeListener(l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        change.removePropertyChangeListener(l);
    }

    protected void _getShips(Collection<String> names, Collection<Ship> ships) {
        _getShips(names, ships, ShipViewMode.Default);
    }

    protected void _getShips(Collection<String> names, Collection<Ship> ships, ShipViewMode viewMode) {
        GameData attachedGameData = requireGameData();
        for (String name : names) {
            Ship ship = attachedGameData.ship(name);
            if (ship != null) {
                switch (viewMode) {
                    case OneTime:
                        ships.add(ship.getOneTimeShip());
                        break;
                    case StarshipTrait:
                        if (ship.hasTrait()) {
                            ships.add(ship);
                        }
                        break;
                    case Default:
                    default:
                        ships.add(ship);
                        break;
                }
            }
        }
    }

    /**
     * Canonicalizes saved Ship names and removes unknown Ships without changing shared GameData values.
     *
     * @throws IllegalStateException if GameData has not been attached
     */
    public void validateShips() {
        requireGameData();
        validateShipNames(active);
        validateShipNames(maintenance);
        validateShipNames(oneTime);

        Map<String, Integer> validatedUsage = new HashMap<String, Integer>();
        for (Map.Entry<String, Integer> entry : usage.entrySet()) {
            Ship ship = gameData.ship(entry.getKey());
            if (ship != null) {
                String canonicalName = ship.getName();
                int previousCount = validatedUsage.getOrDefault(canonicalName, 0);
                validatedUsage.put(canonicalName, previousCount + entry.getValue());
            }
        }
        usage.clear();
        usage.putAll(validatedUsage);
    }

    /**
     * Replaces known saved names with canonical names and drops unknown entries in place.
     *
     * @param names saved Ship names to validate
     */
    private void validateShipNames(List<String> names) {
        List<String> validatedNames = new ArrayList<String>();
        for (String name : names) {
            Ship ship = gameData.ship(name);
            if (ship != null) {
                validatedNames.add(ship.getName());
            }
        }
        names.clear();
        names.addAll(validatedNames);
    }

    /**
     * Returns the attached reference data or fails at the first lookup-dependent operation.
     *
     * @return the attached GameData
     * @throws IllegalStateException if this Admiral has not been attached
     */
    private GameData requireGameData() {
        if (gameData == null) {
            throw new IllegalStateException("Admiral must be attached to GameData before resolving Ships");
        }
        return gameData;
    }

    /**
     * Validates compatibility name collections before either startup restoration or an attached Roster mutation.
     *
     * @param names names to validate
     * @param argumentName collection name used in null diagnostics
     * @throws NullPointerException if {@code names} or one of its elements is null
     */
    private static void requireNames(Collection<String> names, String argumentName) {
        Objects.requireNonNull(names, argumentName);
        for (String name : names) {
            Objects.requireNonNull(name, argumentName + " contains null");
        }
    }

    /**
     * For each ship that has completed it's maintenance,
     * 1) Remove the ship from the maintenance list.
     * 2) Add the the ship to the active ship list.
     */
    public void activateShips() {
		/*
		long currentTime = System.currentTimeMillis();
		for (Map.Entry<String, Long> entry : getSchedule().entrySet()) {
			String shipname = entry.getKey();
			long time = entry.getValue();
			if (time < currentTime && maintenance.contains(shipname)) {
				maintenance.remove(shipname);
				active.add(shipname);
			}
		}*/
    }

    protected void setShips(List<String> names, Set<Ship> ships) {
        names.clear();
        addShips(names, ships);
    }

    protected void addShips(List<String> names, Collection<Ship> ships) {
        for (Ship ship : ships) {
            names.add(ship.getName());
        }
    }

    protected void removeShips(List<String> names, Collection<Ship> ships) {
        for (Ship ship : ships) {
            names.remove(ship.getName());
        }
    }

    protected void useShip(String shipName) {
        int count = 0;
        if (usage.containsKey(shipName)) {
            count = usage.get(shipName);
        }
        usage.put(shipName, count + 1);
    }

    @Override
    public String toString() {
        return name + " {\n\tFaction: " + faction + "\n\tActive Ships: " +
                active.size() + "\n\tMaintenance Ships: " + maintenance.size() +
                "\n\tOne Time Ships: " + oneTime.size() +
                "\n\tShip Usage: " + usage.size() + "\n}";
    }

}
