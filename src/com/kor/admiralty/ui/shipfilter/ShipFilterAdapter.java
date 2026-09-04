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

/**
 * Internal adaptation seam from one supported entry type to canonical Ship
 * facts and its paired ordering type.
 *
 * @param <E> entry type projected by the filter
 * @param <O> ordering type supported by that entry
 */
interface ShipFilterAdapter<E, O> {

    /**
     * Returns the canonical Ship facts that govern one entry's visibility.
     *
     * @param entry supported entry
     * @return canonical Ship facts
     */
    Ship ship(E entry);

    /**
     * Returns the complete comparator for a supported ordering value.
     *
     * @param order requested ordering
     * @return comparator over this adapter's entry type
     */
    Comparator<E> comparator(O order);
}
