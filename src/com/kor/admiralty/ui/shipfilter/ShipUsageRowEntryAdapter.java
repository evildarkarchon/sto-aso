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
package com.kor.admiralty.ui.shipfilter;

import java.util.Comparator;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipUsageRow;
import com.kor.admiralty.enums.ShipUsageSortOrder;

/**
 * Internal adaptation of immutable usage rows to canonical Ship facts and
 * deployment-count ordering.
 */
final class ShipUsageRowEntryAdapter implements ShipFilterAdapter<ShipUsageRow, ShipUsageSortOrder> {

    static final ShipUsageRowEntryAdapter INSTANCE = new ShipUsageRowEntryAdapter();

    private static final Comparator<ShipUsageRow> DEFAULT_ORDER = Comparator.comparing(
            ShipUsageRow::ship,
            ShipEntryAdapter.CANONICAL_ORDER);
    private static final Comparator<ShipUsageRow> MOST_USED_ORDER = Comparator
            .comparingInt(ShipUsageRow::deploymentCount)
            .reversed()
            .thenComparing(ShipUsageRow::ship, ShipEntryAdapter.CANONICAL_ORDER);
    private static final Comparator<ShipUsageRow> LEAST_USED_ORDER = Comparator
            .comparingInt(ShipUsageRow::deploymentCount)
            .thenComparing(ShipUsageRow::ship, ShipEntryAdapter.CANONICAL_ORDER);

    private ShipUsageRowEntryAdapter() {
    }

    @Override
    public Ship ship(ShipUsageRow row) {
        return row.ship();
    }

    @Override
    public Comparator<ShipUsageRow> comparator(ShipUsageSortOrder order) {
        return switch (order) {
            case Default -> DEFAULT_ORDER;
            case MostUsed -> MOST_USED_ORDER;
            case LeastUsed -> LEAST_USED_ORDER;
        };
    }
}
