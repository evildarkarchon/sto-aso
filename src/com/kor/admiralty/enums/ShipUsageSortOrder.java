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
package com.kor.admiralty.enums;

import java.util.Comparator;

import com.kor.admiralty.beans.LeastUsedShipComparator;
import com.kor.admiralty.beans.MostUsedShipComparator;
import com.kor.admiralty.beans.ShipUsageRow;

/**
 * Available Ship Statistics orderings over immutable usage rows.
 */
public enum ShipUsageSortOrder {

    Default((left, right) -> left.ship().compareTo(right.ship())),
    MostUsed(new MostUsedShipComparator()),
    LeastUsed(new LeastUsedShipComparator());

    private final Comparator<ShipUsageRow> comparator;

    ShipUsageSortOrder(Comparator<ShipUsageRow> comparator) {
        this.comparator = comparator;
    }

    /**
     * Returns the immutable-row comparator for this order.
     *
     * @return row comparator
     */
    public Comparator<ShipUsageRow> comparator() {
        return comparator;
    }
}
