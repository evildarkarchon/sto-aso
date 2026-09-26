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

import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.RosterCardKind;
import com.kor.admiralty.ui.artwork.ShipArtwork;

/**
 * Centralizes the card-kind presentation shared by list and embedded Assignment renderers.
 */
final class RosterCardPresentation {

    private RosterCardPresentation() {
    }

    /**
     * Returns the historical display name for one immutable Roster card.
     *
     * @param card card to present, or null for an empty slot
     * @return canonical reusable name, One-Time quantity-prefixed name, or null
     */
    static String displayName(RosterCard card) {
        if (card == null) {
            return null;
        }
        return card.getKind() == RosterCardKind.ONE_TIME
                ? "(1x) " + card.getShip().getName()
                : card.getShip().getDisplayName();
    }

    /**
     * Selects specific artwork for reusable cards and generic artwork for One-Time cards.
     *
     * @param card card to present, or null for an empty slot
     * @return the named presentation for the card kind
     */
    static ShipArtwork.Presentation artworkPresentation(RosterCard card) {
        return card != null && card.getKind() == RosterCardKind.REUSABLE
                ? ShipArtwork.Presentation.SPECIFIC : ShipArtwork.Presentation.GENERIC;
    }
}
