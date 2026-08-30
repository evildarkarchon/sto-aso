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
package com.kor.admiralty.ui.renderers;

import java.awt.Component;

import javax.swing.JList;
import javax.swing.ListCellRenderer;

import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.RosterCardKind;
import com.kor.admiralty.beans.Ship;

/**
 * Renders immutable Roster cards while obtaining all visual facts from their canonical Ships.
 */
public final class RosterCardCellRenderer implements ListCellRenderer<RosterCard> {

    private final BasicShipCellRenderer delegate;

    /**
     * Creates a card renderer around one Ship-facts renderer.
     *
     * @param delegate renderer responsible for the canonical Ship presentation
     */
    private RosterCardCellRenderer(BasicShipCellRenderer delegate) {
        this.delegate = delegate;
    }

    /**
     * Creates the standard Admiralty-card presentation for Roster lists.
     *
     * @return a renderer that accepts immutable Roster cards
     */
    public static ListCellRenderer<RosterCard> shipCards() {
        return new RosterCardCellRenderer(new ShipCellRenderer());
    }

    /**
     * Creates the Starship Trait presentation for reusable Roster cards.
     *
     * @return a trait renderer that accepts immutable Roster cards
     */
    public static ListCellRenderer<RosterCard> traitCards() {
        return new RosterCardCellRenderer(new StarshipTraitCellRenderer());
    }

    @Override
    public Component getListCellRendererComponent(
            JList<? extends RosterCard> list,
            RosterCard card,
            int index,
            boolean isSelected,
            boolean cellHasFocus) {
        Ship ship = card == null ? null : card.getShip();
        String displayName = ship == null
                ? null
                : card.getKind() == RosterCardKind.ONE_TIME
                        ? "(1x) " + ship.getName()
                        : ship.getDisplayName();
        return delegate.renderShip(ship, displayName, card != null, isSelected);
    }
}
