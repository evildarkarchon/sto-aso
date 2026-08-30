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
package com.kor.admiralty.beans;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.kor.admiralty.io.GameData;

/**
 * Internal reusable-card state machine. Admiral remains the only caller-facing mutation seam.
 * Mutations are caller-thread-confined; retained views provide stable snapshots without internal locking.
 */
final class Roster {

    private final GameData gameData;
    private WorkingState state;
    private long revision;
    private RosterView view;

    /**
     * Groups the mutable structures that must be copied and committed as one Roster state.
     */
    private static final class WorkingState {

        private final Map<String, RosterCard> cardsByShipName;
        private final List<String> activeOrder;
        private final List<String> maintenanceOrder;

        private WorkingState() {
            cardsByShipName = new LinkedHashMap<String, RosterCard>();
            activeOrder = new ArrayList<String>();
            maintenanceOrder = new ArrayList<String>();
        }

        private WorkingState(WorkingState source) {
            cardsByShipName = new LinkedHashMap<String, RosterCard>(source.cardsByShipName);
            activeOrder = new ArrayList<String>(source.activeOrder);
            maintenanceOrder = new ArrayList<String>(source.maintenanceOrder);
        }
    }

    private Roster(GameData gameData) {
        this.gameData = Objects.requireNonNull(gameData, "gameData");
        state = new WorkingState();
        revision = 0L;
        view = snapshot(revision, state);
    }

    /**
     * Restores canonical reusable cards without treating startup state as a planning change.
     * Maintenance is applied last so it wins any conflicting historical Active entry.
     *
     * @param gameData canonical Ship reference data
     * @param activeNames persisted Active names; unknown names are ignored
     * @param maintenanceNames persisted Maintenance names; unknown names are ignored
     * @return a restored Roster whose initial revision is zero
     * @throws NullPointerException if an argument is null
     */
    static Roster restore(GameData gameData, Collection<String> activeNames, Collection<String> maintenanceNames) {
        Roster roster = new Roster(gameData);
        roster.restoreNames(activeNames, RosterState.ACTIVE);
        roster.restoreNames(maintenanceNames, RosterState.MAINTENANCE);
        roster.view = roster.snapshot(roster.revision, roster.state);
        return roster;
    }

    /**
     * Returns the current immutable Roster snapshot.
     *
     * @return the exact view retained for the current revision
     */
    RosterView view() {
        return view;
    }

    /**
     * Returns canonical names for a present state in compatibility persistence order.
     *
     * @param rosterState Active or Maintenance
     * @return an unmodifiable copy of canonical Ship names
     * @throws IllegalArgumentException if {@code rosterState} is Absent
     * @throws NullPointerException if {@code rosterState} is null
     */
    List<String> names(RosterState rosterState) {
        return Collections.unmodifiableList(new ArrayList<String>(orderFor(rosterState)));
    }

    /**
     * Adds canonical reusable Ships to one state, atomically moving cards already in the other state.
     *
     * @param ships Ships to canonicalize through this Roster's GameData
     * @param destination Active or Maintenance
     * @return the committed before/after change, or null when every Ship is already at the destination
     * @throws IllegalArgumentException if a Ship is unknown or the destination is Absent
     * @throws NullPointerException if an argument or collection element is null
     */
    RosterChange addReusableShips(Collection<Ship> ships, RosterState destination) {
        requirePresentState(destination);
        Map<String, Ship> canonicalShips = canonicalShips(ships);

        WorkingState updated = new WorkingState(state);
        boolean changed = false;
        for (Ship ship : canonicalShips.values()) {
            String name = ship.getName();
            RosterCard current = updated.cardsByShipName.get(name);
            if (current != null && current.getState() == destination) {
                continue;
            }

            if (current != null) {
                orderFor(current.getState(), updated).remove(name);
                updated.cardsByShipName.put(name, new RosterCard(current.getId(), ship, destination));
            } else {
                updated.cardsByShipName.put(name, new RosterCard(RosterCardId.create(), ship, destination));
            }
            orderFor(destination, updated).add(name);
            changed = true;
        }
        if (!changed) {
            return null;
        }

        return commit(updated);
    }

    /**
     * Moves identity-bearing reusable cards to one destination as a single committed change.
     *
     * @param cards current cards selected from this Roster's retained or prior views
     * @param destination Active or Maintenance
     * @return the committed before/after change, or null when every card is already at the destination
     * @throws IllegalArgumentException if a card is foreign or removed, or the destination is Absent
     * @throws NullPointerException if an argument or collection element is null
     */
    RosterChange moveReusableCards(Collection<RosterCard> cards, RosterState destination) {
        requirePresentState(destination);
        Map<RosterCardId, String> selectedNames = currentNamesFor(cards);
        WorkingState updated = new WorkingState(state);
        boolean changed = false;
        for (String name : selectedNames.values()) {
            RosterCard current = updated.cardsByShipName.get(name);
            if (current.getState() == destination) {
                continue;
            }
            orderFor(current.getState(), updated).remove(name);
            orderFor(destination, updated).add(name);
            updated.cardsByShipName.put(name, new RosterCard(current.getId(), current.getShip(), destination));
            changed = true;
        }
        if (!changed) {
            return null;
        }

        return commit(updated);
    }

    /**
     * Removes identity-bearing reusable cards as a single committed change.
     *
     * @param cards current cards selected from this Roster's retained or prior views
     * @return the committed before/after change, or null when {@code cards} is empty
     * @throws IllegalArgumentException if a card is foreign or removed
     * @throws NullPointerException if {@code cards} or one of its elements is null
     */
    RosterChange removeReusableCards(Collection<RosterCard> cards) {
        Map<RosterCardId, String> selectedNames = currentNamesFor(cards);
        if (selectedNames.isEmpty()) {
            return null;
        }

        WorkingState updated = new WorkingState(state);
        for (String name : selectedNames.values()) {
            RosterCard removed = updated.cardsByShipName.remove(name);
            orderFor(removed.getState(), updated).remove(name);
        }

        return commit(updated);
    }

    /**
     * Replaces one reusable state for persistence and legacy callers while preserving the other state's cards.
     * Input order alone is not planning-relevant and therefore does not create a new revision.
     *
     * @param ships complete replacement membership for the destination
     * @param destination Active or Maintenance
     * @return the committed before/after change, or null when membership and states are unchanged
     * @throws IllegalArgumentException if a Ship is unknown or the destination is Absent
     * @throws NullPointerException if an argument or collection element is null
     */
    RosterChange replaceReusableShips(Collection<Ship> ships, RosterState destination) {
        requirePresentState(destination);
        Map<String, Ship> canonicalShips = canonicalShips(ships);
        List<String> replacementOrder = new ArrayList<String>(canonicalShips.keySet());

        WorkingState updated = new WorkingState(state);
        List<String> destinationOrder = orderFor(destination, updated);
        boolean changed = destinationOrder.size() != canonicalShips.size()
                || !canonicalShips.keySet().containsAll(destinationOrder);

        for (String currentName : new ArrayList<String>(destinationOrder)) {
            if (!canonicalShips.containsKey(currentName)) {
                updated.cardsByShipName.remove(currentName);
                changed = true;
            }
        }
        for (Ship ship : canonicalShips.values()) {
            String name = ship.getName();
            RosterCard current = updated.cardsByShipName.get(name);
            if (current == null) {
                updated.cardsByShipName.put(name, new RosterCard(RosterCardId.create(), ship, destination));
                changed = true;
            } else if (current.getState() != destination) {
                orderFor(current.getState(), updated).remove(name);
                updated.cardsByShipName.put(name, new RosterCard(current.getId(), ship, destination));
                changed = true;
            }
        }
        if (!changed) {
            return null;
        }
        destinationOrder.clear();
        destinationOrder.addAll(replacementOrder);

        return commit(updated);
    }

    /**
     * Restores known canonical names directly into the startup state without advancing its revision.
     *
     * @param names persisted names to restore; unknown names are ignored
     * @param destination Active or Maintenance
     * @throws NullPointerException if {@code names} is null
     */
    private void restoreNames(Collection<String> names, RosterState destination) {
        Objects.requireNonNull(names, "names");
        for (String name : names) {
            Ship canonicalShip = gameData.ship(name);
            if (canonicalShip == null) {
                continue;
            }
            String canonicalName = canonicalShip.getName();
            RosterCard current = state.cardsByShipName.get(canonicalName);
            if (current != null && current.getState() == destination) {
                continue;
            }
            if (current != null) {
                orderFor(current.getState()).remove(canonicalName);
                state.cardsByShipName.put(
                        canonicalName,
                        new RosterCard(current.getId(), canonicalShip, destination));
            } else {
                state.cardsByShipName.put(
                        canonicalName,
                        new RosterCard(RosterCardId.create(), canonicalShip, destination));
            }
            orderFor(destination).add(canonicalName);
        }
    }

    /**
     * Resolves and deduplicates Ships through this Roster's reference data before any mutation begins.
     *
     * @param ships Ship-shaped inputs to resolve
     * @return canonical Ships keyed by canonical name in first-occurrence order
     * @throws IllegalArgumentException if a Ship is absent from the reference data
     * @throws NullPointerException if {@code ships} or one of its elements is null
     */
    private Map<String, Ship> canonicalShips(Collection<Ship> ships) {
        Objects.requireNonNull(ships, "ships");
        Map<String, Ship> canonicalShips = new LinkedHashMap<String, Ship>();
        for (Ship ship : ships) {
            Objects.requireNonNull(ship, "ships contains null");
            Ship canonicalShip = gameData.ship(ship.getName());
            if (canonicalShip == null) {
                throw new IllegalArgumentException("Ship is not present in this Admiral's GameData: " + ship.getName());
            }
            canonicalShips.putIfAbsent(canonicalShip.getName(), canonicalShip);
        }
        return canonicalShips;
    }

    /**
     * Resolves selected opaque identities against the current Roster before any mutation begins.
     *
     * @param cards cards from this Roster's current or retained views
     * @return selected current names keyed by identity, with duplicate inputs removed
     * @throws IllegalArgumentException if a card is foreign or has been removed
     * @throws NullPointerException if {@code cards} or one of its elements is null
     */
    private Map<RosterCardId, String> currentNamesFor(Collection<RosterCard> cards) {
        Objects.requireNonNull(cards, "cards");
        Map<RosterCardId, String> currentNames = new LinkedHashMap<RosterCardId, String>();
        for (Map.Entry<String, RosterCard> entry : state.cardsByShipName.entrySet()) {
            currentNames.put(entry.getValue().getId(), entry.getKey());
        }

        Map<RosterCardId, String> selectedNames = new LinkedHashMap<RosterCardId, String>();
        for (RosterCard card : cards) {
            Objects.requireNonNull(card, "cards contains null");
            String currentName = currentNames.get(card.getId());
            if (currentName == null) {
                throw new IllegalArgumentException("Roster card does not belong to this Admiral");
            }
            selectedNames.putIfAbsent(card.getId(), currentName);
        }
        return selectedNames;
    }

    /**
     * Builds the complete next view before swapping fields, then commits every mutable reference together.
     *
     * @param updated fully prepared mutable state owned by this Roster after the call
     * @return one immutable before/after change for listener delivery
     * @throws ArithmeticException if the revision counter overflows
     */
    private RosterChange commit(WorkingState updated) {
        long updatedRevision = Math.incrementExact(revision);
        RosterView updatedView = snapshot(updatedRevision, updated);
        RosterView before = view;
        state = updated;
        revision = updatedRevision;
        view = updatedView;
        return new RosterChange(before, updatedView);
    }

    /**
     * Projects a complete immutable public view from one coherent working state.
     *
     * @param snapshotRevision revision represented by the state
     * @param snapshotState source state that will no longer be mutated after commit
     * @return a naturally ordered immutable Roster view
     */
    private RosterView snapshot(long snapshotRevision, WorkingState snapshotState) {
        List<RosterCard> activeCards = cardsInNaturalOrder(
                snapshotState.activeOrder,
                snapshotState.cardsByShipName);
        List<RosterCard> maintenanceCards = cardsInNaturalOrder(
                snapshotState.maintenanceOrder,
                snapshotState.cardsByShipName);
        return new RosterView(snapshotRevision, activeCards, maintenanceCards);
    }

    /**
     * Projects cards for one state into stable natural Ship order.
     *
     * @param names canonical names in compatibility persistence order
     * @param snapshotCards cards keyed by canonical name
     * @return cards sorted by their canonical Ship ordering
     */
    private List<RosterCard> cardsInNaturalOrder(
            List<String> names,
            Map<String, RosterCard> snapshotCards) {
        List<RosterCard> cards = new ArrayList<RosterCard>();
        for (String name : names) {
            cards.add(snapshotCards.get(name));
        }
        cards.sort((left, right) -> left.getShip().compareTo(right.getShip()));
        return cards;
    }

    /**
     * Returns the mutable compatibility order for one present state.
     *
     * @param rosterState Active or Maintenance
     * @return the order list owned by the current working state
     * @throws IllegalArgumentException if {@code rosterState} is Absent
     * @throws NullPointerException if {@code rosterState} is null
     */
    private List<String> orderFor(RosterState rosterState) {
        return orderFor(rosterState, state);
    }

    /**
     * Returns the mutable compatibility order for one present state in a supplied working copy.
     *
     * @param rosterState Active or Maintenance
     * @param workingState state whose order list is required
     * @return the selected mutable order list
     * @throws IllegalArgumentException if {@code rosterState} is Absent
     * @throws NullPointerException if {@code rosterState} or {@code workingState} is null
     */
    private static List<String> orderFor(
            RosterState rosterState,
            WorkingState workingState) {
        Objects.requireNonNull(rosterState, "state");
        Objects.requireNonNull(workingState, "workingState");
        if (rosterState == RosterState.ACTIVE) {
            return workingState.activeOrder;
        }
        if (rosterState == RosterState.MAINTENANCE) {
            return workingState.maintenanceOrder;
        }
        throw new IllegalArgumentException("Absent reusable Ships do not have a Roster order");
    }

    /**
     * Rejects Absent as a destination because absence is represented by removal, not a stored card state.
     *
     * @param rosterState requested mutation destination
     * @throws IllegalArgumentException if {@code rosterState} is Absent
     * @throws NullPointerException if {@code rosterState} is null
     */
    private static void requirePresentState(RosterState rosterState) {
        Objects.requireNonNull(rosterState, "state");
        if (rosterState == RosterState.ABSENT) {
            throw new IllegalArgumentException("Use a remove operation to make a reusable Ship absent");
        }
    }
}
