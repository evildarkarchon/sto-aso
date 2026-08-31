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

import java.util.Comparator;

/**
 * Centralizes deployment-count ordering and deterministic natural Ship
 * tie-breaking for statistics rows.
 */
abstract class DeploymentCountShipComparator implements Comparator<ShipUsageRow> {

    private final boolean descending;
    private final ShipComparator shipComparator = new ShipComparator();

    /**
     * Creates a deployment-count comparator in the requested direction.
     *
     * @param descending whether larger counts sort before smaller counts
     */
    protected DeploymentCountShipComparator(boolean descending) {
        this.descending = descending;
    }

    @Override
    public final int compare(ShipUsageRow left, ShipUsageRow right) {
        int countComparison = descending
                ? Integer.compare(right.deploymentCount(), left.deploymentCount())
                : Integer.compare(left.deploymentCount(), right.deploymentCount());
        return countComparison != 0
                ? countComparison
                : shipComparator.compare(left.ship(), right.ship());
    }
}
