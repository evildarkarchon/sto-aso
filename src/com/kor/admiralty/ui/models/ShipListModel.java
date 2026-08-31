/*******************************************************************************
 * Copyright (C) 2015, 2019 Dave Kor
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/
package com.kor.admiralty.ui.models;

import java.io.Serial;
import java.util.Collection;
import java.util.Comparator;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.enums.ShipSortOrder;

/**
 * Filters and sorts canonical Ship list entries for Roster and selection
 * screens.
 */
public class ShipListModel extends AbstractShipListModel<Ship, ShipSortOrder> {

    @Serial
    private static final long serialVersionUID = 7434906615168264076L;

    /**
     * Creates an empty naturally ordered Ship model.
     */
    public ShipListModel() {
        super(ShipSortOrder.Default);
    }

    /**
     * Creates a naturally ordered Ship model from a copied collection.
     *
     * @param ships initial canonical Ships
     */
    public ShipListModel(Collection<Ship> ships) {
        super(ships, ShipSortOrder.Default);
    }

    @Override
    protected Ship ship(Ship ship) {
        return ship;
    }

    @Override
    protected Comparator<Ship> comparator(ShipSortOrder sortOrder) {
        return sortOrder.comparator();
    }

    /**
     * Removes every canonical Ship from this model.
     */
    public void removeAllShips() {
        removeAllEntries();
    }

    /**
     * Replaces this model's canonical Ships and rebuilds its visible state.
     *
     * @param ships replacement canonical Ships
     */
    public void setShips(Collection<Ship> ships) {
        setEntries(ships);
    }

    /**
     * Adds canonical Ships and rebuilds this model's visible state.
     *
     * @param ships canonical Ships to add
     */
    public void addShips(Collection<Ship> ships) {
        addEntries(ships);
    }

    /**
     * @return current canonical Ship sort order
     */
    public ShipSortOrder getShipSortOrder() {
        return getSortOrder();
    }

    /**
     * Changes canonical Ship ordering and rebuilds this model's visible state.
     *
     * @param sortOrder replacement Ship sort order
     */
    public void setShipSortOrder(ShipSortOrder sortOrder) {
        setSortOrder(sortOrder);
    }
}
