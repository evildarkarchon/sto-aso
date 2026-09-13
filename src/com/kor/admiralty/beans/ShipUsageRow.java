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
 * Immutable Ship Statistics value combining canonical Ship facts, aggregate
 * deployments, and current Roster membership.
 */
public record ShipUsageRow(Ship ship, int deploymentCount, boolean inCurrentRoster) {

    /**
     * Creates one usage snapshot row without copying its canonical GameData Ship.
     *
     * @param ship            canonical Ship represented by this row
     * @param deploymentCount non-negative aggregate deployments across the selected
     *                        Admirals
     * @param inCurrentRoster whether this Ship type occurs in any selected current
     *                        Roster
     * @throws NullPointerException     if {@code ship} is null
     * @throws IllegalArgumentException if {@code deploymentCount} is negative
     */
    public ShipUsageRow(Ship ship, int deploymentCount, boolean inCurrentRoster) {
        this.ship = Objects.requireNonNull(ship, "ship");
        if (deploymentCount < 0) {
            throw new IllegalArgumentException("deploymentCount must be non-negative");
        }
        this.deploymentCount = deploymentCount;
        this.inCurrentRoster = inCurrentRoster;
    }

    /**
     * Returns the canonical GameData Ship whose reference facts should be rendered.
     *
     * @return canonical Ship instance
     */
    @Override
    public Ship ship() {
        return ship;
    }

    /**
     * Returns aggregate deployments across the Admirals selected for this snapshot.
     *
     * @return non-negative deployment count
     */
    @Override
    public int deploymentCount() {
        return deploymentCount;
    }

    /**
     * Reports whether the Ship type occurs in at least one selected current Roster.
     *
     * @return {@code true} for current Roster membership; {@code false} for
     * historical-only rows
     */
    @Override
    public boolean inCurrentRoster() {
        return inCurrentRoster;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ShipUsageRow(Ship ship1, int count, boolean currentRoster))) {
            return false;
        }
        return deploymentCount == count
                && inCurrentRoster == currentRoster
                && ship.equals(ship1);
    }

    @Override
    public String toString() {
        return ship.getName() + " {deployments=" + deploymentCount + ", inCurrentRoster=" + inCurrentRoster + "}";
    }
}
