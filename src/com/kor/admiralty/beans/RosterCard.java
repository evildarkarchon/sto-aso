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
 * Immutable runtime value for one identity-bearing Roster card at a particular revision.
 */
public final class RosterCard {

    private final RosterCardId id;
    private final Ship ship;
    private final RosterCardKind kind;
    private final RosterState state;

    /**
     * Creates a reusable card for an Active or Maintenance snapshot.
     *
     * @param id opaque runtime identity
     * @param ship canonical Ship facts
     * @param state Active or Maintenance
     * @throws IllegalArgumentException if {@code state} is not Active or Maintenance
     * @throws NullPointerException if an argument is null
     */
    RosterCard(RosterCardId id, Ship ship, RosterState state) {
        this(id, ship, RosterCardKind.REUSABLE, state);
    }

    /**
     * Creates a present card and enforces the legal kind/state pair before publication.
     *
     * @param id opaque runtime identity
     * @param ship canonical Ship facts
     * @param kind reusable or One-Time
     * @param state state appropriate to {@code kind}
     * @throws IllegalArgumentException if the kind/state pair cannot describe a present card
     * @throws NullPointerException if an argument is null
     */
    RosterCard(RosterCardId id, Ship ship, RosterCardKind kind, RosterState state) {
        this.id = Objects.requireNonNull(id, "id");
        this.ship = Objects.requireNonNull(ship, "ship");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.state = Objects.requireNonNull(state, "state");
        if (state == RosterState.ABSENT
                || (kind == RosterCardKind.REUSABLE && state == RosterState.ONE_TIME)
                || (kind == RosterCardKind.ONE_TIME && state != RosterState.ONE_TIME)) {
            throw new IllegalArgumentException("Roster card kind and state must describe a present card");
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
     * Returns whether this card is reusable or one independently selectable One-Time copy.
     *
     * @return card kind
     */
    public RosterCardKind getKind() {
        return kind;
    }

    /**
     * Returns this card's state in the snapshot that produced it.
     *
     * @return Active, Maintenance, or One-Time
     */
    public RosterState getState() {
        return state;
    }
}
