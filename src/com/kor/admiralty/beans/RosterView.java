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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, internally consistent view of one Admiral's reusable Roster at one revision.
 * Canonical GameData Ship references are retained rather than copied during the current migration phase.
 */
public final class RosterView {

    private final long revision;
    private final List<RosterCard> activeCards;
    private final List<RosterCard> maintenanceCards;
    private final List<RosterCard> reusableCards;
    private final Map<String, RosterState> statesByShipName;

    RosterView(long revision, List<RosterCard> activeCards, List<RosterCard> maintenanceCards) {
        this.revision = revision;
        this.activeCards = immutableCopy(activeCards);
        this.maintenanceCards = immutableCopy(maintenanceCards);
        List<RosterCard> allCards = new ArrayList<RosterCard>(activeCards.size() + maintenanceCards.size());
        allCards.addAll(activeCards);
        allCards.addAll(maintenanceCards);
        reusableCards = Collections.unmodifiableList(allCards);

        Map<String, RosterState> states = new HashMap<String, RosterState>();
        for (RosterCard card : allCards) {
            states.put(card.getShip().getName(), card.getState());
        }
        statesByShipName = Collections.unmodifiableMap(states);
    }

    /**
     * Returns the planning revision represented by every collection in this view.
     *
     * @return non-negative reusable Roster revision
     */
    public long getRevision() {
        return revision;
    }

    /**
     * Returns naturally ordered reusable cards that are currently Active.
     *
     * @return unmodifiable Active-card list
     */
    public List<RosterCard> getActiveCards() {
        return activeCards;
    }

    /**
     * Returns naturally ordered reusable cards that are currently in Maintenance.
     *
     * @return unmodifiable Maintenance-card list
     */
    public List<RosterCard> getMaintenanceCards() {
        return maintenanceCards;
    }

    /**
     * Returns all present reusable cards, grouped as Active then Maintenance.
     *
     * @return unmodifiable reusable-card list
     */
    public List<RosterCard> getReusableCards() {
        return reusableCards;
    }

    /**
     * Reports the mutually exclusive reusable state for a canonical Ship name in this snapshot.
     *
     * @param ship Ship whose reusable state is requested
     * @return Active, Maintenance, or Absent
     * @throws NullPointerException if {@code ship} is null
     */
    public RosterState getReusableState(Ship ship) {
        Objects.requireNonNull(ship, "ship");
        return statesByShipName.getOrDefault(ship.getName(), RosterState.ABSENT);
    }

    private static List<RosterCard> immutableCopy(List<RosterCard> cards) {
        return Collections.unmodifiableList(new ArrayList<RosterCard>(cards));
    }
}
