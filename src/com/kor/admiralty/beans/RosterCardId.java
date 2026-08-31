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
import java.util.UUID;

/**
 * Opaque runtime identity for one Roster card. Identities are deliberately not
 * persisted.
 */
public final class RosterCardId {

    private final UUID owner;
    private final UUID value;

    private RosterCardId(UUID owner, UUID value) {
        this.owner = owner;
        this.value = value;
    }

    /**
     * Creates an identity scoped to one Admiral's Roster for the current runtime.
     *
     * @param owner opaque Roster scope used only for misuse validation
     * @return a new opaque card identity
     * @throws NullPointerException if {@code owner} is null
     */
    static RosterCardId create(UUID owner) {
        return new RosterCardId(Objects.requireNonNull(owner, "owner"), UUID.randomUUID());
    }

    /**
     * Reports whether this identity was issued by one Roster without exposing the
     * scope publicly.
     *
     * @param expectedOwner Roster scope to compare
     * @return {@code true} when this card belongs to that Roster
     * @throws NullPointerException if {@code expectedOwner} is null
     */
    boolean isOwnedBy(UUID expectedOwner) {
        return owner.equals(Objects.requireNonNull(expectedOwner, "expectedOwner"));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RosterCardId)) {
            return false;
        }
        RosterCardId that = (RosterCardId) other;
        return owner.equals(that.owner) && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, value);
    }
}
