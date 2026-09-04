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

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.enums.ShipSortOrder;

import java.util.Comparator;

/**
 * Internal canonical Ship adaptation and ordering policy.
 */
final class ShipEntryAdapter implements ShipFilterAdapter<Ship, ShipSortOrder> {

    static final ShipEntryAdapter INSTANCE = new ShipEntryAdapter();

    static final Comparator<Ship> CANONICAL_ORDER = Comparator
            .comparing(Ship::getTier)
            .thenComparing(Ship::getRarity)
            .thenComparing(Ship::getRole)
            .thenComparing(Ship::getName);

    private ShipEntryAdapter() {
    }

    @Override
    public Ship ship(Ship ship) {
        return ship;
    }

    @Override
    public Comparator<Ship> comparator(ShipSortOrder order) {
        return switch (order) {
            case Default -> CANONICAL_ORDER;
        };
    }
}
