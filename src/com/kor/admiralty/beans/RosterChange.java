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
 * Immutable before/after snapshots for one committed reusable Roster operation.
 */
public final class RosterChange {

    private final RosterView before;
    private final RosterView after;

    RosterChange(RosterView before, RosterView after) {
        this.before = Objects.requireNonNull(before, "before");
        this.after = Objects.requireNonNull(after, "after");
    }

    /**
     * Returns the complete reusable Roster snapshot immediately before the commit.
     *
     * @return immutable pre-commit view
     */
    public RosterView getBefore() {
        return before;
    }

    /**
     * Returns the complete reusable Roster snapshot immediately after the commit.
     *
     * @return immutable post-commit view
     */
    public RosterView getAfter() {
        return after;
    }
}
