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
 * Opaque runtime identity for one Roster card. Identities are deliberately not persisted.
 */
public final class RosterCardId {

    private final UUID value;

    private RosterCardId(UUID value) {
        this.value = value;
    }

    /**
     * Creates an identity that is unique across Admirals for the current runtime.
     *
     * @return a new opaque card identity
     */
    static RosterCardId create() {
        return new RosterCardId(UUID.randomUUID());
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
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
