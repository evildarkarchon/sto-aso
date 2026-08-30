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

import java.util.Objects;

/**
 * Immutable runtime value for one reusable Roster card at a particular revision.
 */
public final class RosterCard {

    private final RosterCardId id;
    private final Ship ship;
    private final RosterState state;

    RosterCard(RosterCardId id, Ship ship, RosterState state) {
        this.id = Objects.requireNonNull(id, "id");
        this.ship = Objects.requireNonNull(ship, "ship");
        this.state = Objects.requireNonNull(state, "state");
        if (state == RosterState.ABSENT) {
            throw new IllegalArgumentException("An absent reusable Ship has no Roster card");
        }
    }

    /**
     * Returns the opaque identity that remains stable while this card moves between reusable states.
     *
     * @return runtime-only card identity
     */
    public RosterCardId getId() {
        return id;
    }

    /**
     * Returns the canonical Ship facts supplied by the Admiral's GameData.
     *
     * @return canonical Ship
     */
    public Ship getShip() {
        return ship;
    }

    /**
     * Returns this card's reusable state in the snapshot that produced it.
     *
     * @return Active or Maintenance
     */
    public RosterState getState() {
        return state;
    }
}
