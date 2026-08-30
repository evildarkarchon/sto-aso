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

import java.util.Collection;
import java.util.Comparator;

import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.enums.ShipSortOrder;

/**
 * Filters and sorts immutable identity-bearing Roster cards by their canonical Ship facts.
 */
public final class RosterCardListModel extends AbstractShipListModel<RosterCard, ShipSortOrder> {

    private static final long serialVersionUID = 5781889474581553444L;

    /**
     * Creates an empty naturally ordered Roster-card model.
     */
    public RosterCardListModel() {
        super(ShipSortOrder.Default);
    }

    /**
     * Creates a naturally ordered model from cards captured by one immutable Roster view.
     *
     * @param cards initial identity-bearing cards
     */
    public RosterCardListModel(Collection<RosterCard> cards) {
        super(cards, ShipSortOrder.Default);
    }

    @Override
    protected Ship ship(RosterCard card) {
        return card.getShip();
    }

    @Override
    protected Comparator<RosterCard> comparator(ShipSortOrder sortOrder) {
        Comparator<Ship> shipComparator = sortOrder.comparator();
        return (left, right) -> shipComparator.compare(left.getShip(), right.getShip());
    }

    /**
     * Replaces this model's cards with one immutable Roster projection.
     *
     * @param cards replacement cards from one Roster view
     */
    public void setCards(Collection<RosterCard> cards) {
        setEntries(cards);
    }

    /**
     * Adds cards from one immutable Roster projection.
     *
     * @param cards cards to add
     */
    public void addCards(Collection<RosterCard> cards) {
        addEntries(cards);
    }
}
