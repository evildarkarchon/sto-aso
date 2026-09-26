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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.kor.admiralty.ui.panels;

import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.enums.PlayerFaction;
import com.kor.admiralty.ui.artwork.ShipArtwork;
import com.kor.admiralty.ui.shipfilter.ShipFilterViews;

import java.awt.*;
import java.util.Collection;
import java.util.List;

import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.TitleAddActiveShips;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.TitleAddOneTimeShips;

/**
 * Internal seam for the modal Ship selections initiated by an Admiral workspace.
 * Production delegates to named Ship Filter dialog paths while root integration
 * tests use deterministic selections without opening native windows.
 */
interface RosterSelectionDialog {

    /**
     * Returns the production Swing dialog adapter.
     *
     * @return adapter delegating every selection to the established modal dialogs
     */
    static RosterSelectionDialog swing() {
        return new RosterSelectionDialog() {
            @Override
            public List<Ship> chooseReusableShips(
                    Window owner,
                    PlayerFaction faction,
                    Collection<Ship> candidates,
                    ShipArtwork shipArtwork) {
                return new ShipFilterViews(shipArtwork).chooseReusableShips(
                        owner,
                        faction,
                        candidates,
                        TitleAddActiveShips);
            }

            @Override
            public List<Ship> chooseOneTimeShips(
                    Window owner,
                    PlayerFaction faction,
                    Collection<Ship> candidates,
                    ShipArtwork shipArtwork) {
                return new ShipFilterViews(shipArtwork).chooseOneTimeShips(
                        owner,
                        faction,
                        candidates,
                        TitleAddOneTimeShips);
            }

            @Override
            public List<RosterCard> chooseRosterCards(
                    Window owner,
                    List<RosterCard> candidates,
                    ShipArtwork shipArtwork,
                    String title) {
                return new ShipFilterViews(shipArtwork).chooseRosterCards(owner, candidates, title);
            }
        };
    }

    /**
     * Selects reusable Ships from the supplied GameData candidates.
     *
     * @param owner        owning workspace window, or {@code null} before attachment
     * @param faction      fixed Admiral faction used by the selector
     * @param candidates   reusable Ships not already present in the Roster
     * @param shipArtwork  shared artwork for candidate Ship cards
     * @return selected Ships in dialog order, or an empty list when cancelled
     */
    List<Ship> chooseReusableShips(
            Window owner,
            PlayerFaction faction,
            Collection<Ship> candidates,
            ShipArtwork shipArtwork);

    /**
     * Selects One-Time Ship quantities from the supplied GameData candidates.
     * Repeated Ship entries represent quantities greater than one.
     *
     * @param owner        owning workspace window, or {@code null} before attachment
     * @param faction      fixed Admiral faction used by the selector
     * @param candidates   all Ships available from GameData
     * @param shipArtwork  shared artwork for candidate Ship cards
     * @return selected Ship occurrences in dialog order, or an empty list when cancelled
     */
    List<Ship> chooseOneTimeShips(
            Window owner,
            PlayerFaction faction,
            Collection<Ship> candidates,
            ShipArtwork shipArtwork);

    /**
     * Selects exact displayed Roster cards for a removal action.
     *
     * @param owner        owning workspace window, or {@code null} before attachment
     * @param candidates   exact reusable cards or representative One-Time card types
     * @param shipArtwork  shared artwork for candidate Roster cards
     * @param title        action-specific dialog title
     * @return selected card identities in dialog order, or an empty list when cancelled
     */
    List<RosterCard> chooseRosterCards(
            Window owner,
            List<RosterCard> candidates,
            ShipArtwork shipArtwork,
            String title);
}
