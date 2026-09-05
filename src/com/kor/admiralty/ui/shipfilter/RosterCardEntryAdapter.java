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

import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.enums.ShipSortOrder;

import java.util.Comparator;

/**
 * Internal adaptation of identity-bearing Roster cards to canonical Ship facts.
 */
final class RosterCardEntryAdapter implements ShipFilterAdapter<RosterCard, ShipSortOrder> {

    static final RosterCardEntryAdapter INSTANCE = new RosterCardEntryAdapter();

    private RosterCardEntryAdapter() {
    }

    @Override
    public Ship ship(RosterCard card) {
        return card.getShip();
    }

    @Override
    public Comparator<RosterCard> comparator(ShipSortOrder order) {
        return switch (order) {
            // No identity tie-breaker: stable sorting must retain equal-Ship card order.
            case Default -> Comparator.comparing(RosterCard::getShip, ShipEntryAdapter.CANONICAL_ORDER);
        };
    }
}
