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
package com.kor.admiralty.ui;

import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.RosterView;
import com.kor.admiralty.beans.Ship;

import java.util.*;

/**
 * Builds Swing selection values from immutable Roster views without Ship-shaped card adapters.
 */
public final class RosterCardSelections {

    private RosterCardSelections() {
    }

    /**
     * Projects canonical Ship facts from identity-bearing cards in their current order.
     *
     * @param cards cards captured by one Roster view
     * @return canonical Ships in card order
     */
    public static List<Ship> ships(Collection<RosterCard> cards) {
        List<Ship> ships = new ArrayList<Ship>();
        for (RosterCard card : cards) {
            ships.add(card.getShip());
        }
        return ships;
    }

    /**
     * Retains one representative card per present One-Time Ship type for quantity selection dialogs.
     *
     * @param roster immutable view supplying independently selectable One-Time copies
     * @return one identity-bearing card for each present One-Time Ship type
     */
    public static List<RosterCard> oneTimeShipTypes(RosterView roster) {
        Set<String> includedShipNames = new HashSet<String>();
        List<RosterCard> cardTypes = new ArrayList<RosterCard>();
        for (RosterCard card : roster.getOneTimeCards()) {
            if (includedShipNames.add(card.getShip().getName())) {
                cardTypes.add(card);
            }
        }
        return cardTypes;
    }
}
