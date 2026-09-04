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
package com.kor.admiralty.ui.models;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipUsageRow;
import com.kor.admiralty.enums.ShipUsageSortOrder;

import java.io.Serial;
import java.util.Collection;
import java.util.Comparator;

/**
 * Ship Statistics model whose entries, sorting, and filtering consume immutable
 * usage rows directly.
 */
public final class ShipUsageListModel extends AbstractShipListModel<ShipUsageRow, ShipUsageSortOrder> {

    @Serial
    private static final long serialVersionUID = 5447146595730894528L;

    /**
     * Creates an empty naturally ordered statistics model.
     */
    public ShipUsageListModel() {
        super(ShipUsageSortOrder.Default);
    }

    /**
     * Creates a statistics model from one immutable usage projection snapshot.
     *
     * @param rows projected usage rows copied into this model
     */
    public ShipUsageListModel(Collection<ShipUsageRow> rows) {
        super(rows, ShipUsageSortOrder.Default);
    }

    @Override
    protected Ship ship(ShipUsageRow row) {
        return row.ship();
    }

    @Override
    protected Comparator<ShipUsageRow> comparator(ShipUsageSortOrder sortOrder) {
        return sortOrder.comparator();
    }
}
