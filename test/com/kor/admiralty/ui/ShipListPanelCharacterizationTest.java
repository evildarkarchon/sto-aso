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

import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.enums.*;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.ui.resources.ShipIconFactory;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterizes the established list-only Roster-card selection presentation.
 */
class ShipListPanelCharacterizationTest {

    /**
     * Creates deterministic in-memory artwork without application bootstrap or
     * Icon Cache state.
     *
     * @return isolated Ship artwork adapter
     */
    private static ShipIconFactory testIconRenderer() {
        ImageIcon icon = new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        return (iconName, faction, role, rarity, owned) -> icon;
    }

    /**
     * Creates canonical Ship facts for visible-order assertions.
     *
     * @param name canonical Ship name
     * @return canonical test Ship
     */
    private static Ship ship(String name) {
        return new ShipImpl(
                ShipFaction.Federation,
                Tier.Tier6,
                Rarity.Epic,
                Role.Tac,
                name,
                10,
                20,
                30,
                RuleType.All.rewardBonus(0),
                "");
    }

    /**
     * Finds one exact card for a canonical Ship in the Admiral's One-Time Roster.
     *
     * @param admiral owning Admiral
     * @param ship    canonical Ship carried by the card
     * @return exact card identity
     */
    private static RosterCard cardFor(Admiral admiral, Ship ship) {
        return admiral.getRoster().getOneTimeCards().stream()
                .filter(card -> card.getShip() == ship)
                .findFirst()
                .orElseThrow();
    }

    /**
     * Collects every component of one type from a Swing subtree.
     *
     * @param root          subtree to inspect
     * @param componentType requested component type
     * @param <T>           concrete component type
     * @return matching descendants
     */
    private static <T extends Component> List<T> components(Container root, Class<T> componentType) {
        List<T> matches = new ArrayList<T>();
        for (Component component : root.getComponents()) {
            if (componentType.isInstance(component)) {
                matches.add(componentType.cast(component));
            }
            if (component instanceof Container container) {
                matches.addAll(components(container, componentType));
            }
        }
        return matches;
    }

    /**
     * Verifies Roster-card selection remains list-only, multiple-interval, and
     * returns exact card identities in canonical visible order.
     *
     * @throws Exception if event-thread dispatch fails
     */
    @Test
    void rosterCardDialogReturnsExactCardsInVisibleOrderWithoutShipDetails() throws Exception {
        Ship alpha = ship("Alpha");
        Ship beta = ship("Beta");
        GameData gameData = GameData.builder().ships(List.of(alpha, beta)).build();
        Admiral admiral = new Admiral(gameData);
        admiral.adjustOneTimeShipQuantity(alpha, 1);
        admiral.adjustOneTimeShipQuantity(beta, 1);
        RosterCard alphaCard = cardFor(admiral, alpha);
        RosterCard betaCard = cardFor(admiral, beta);

        SwingUtilities.invokeAndWait(() -> {
            ShipListPanel<RosterCard, ShipSortOrder> panel = ShipListPanel.rosterCards(
                    List.of(betaCard, alphaCard),
                    testIconRenderer());
            JList<?> list = components(panel, JList.class).getFirst();
            list.setSelectedIndices(new int[]{1, 0});

            assertEquals(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION, list.getSelectionMode());
            assertTrue(components(panel, ShipDetailsPanel.class).isEmpty());
            assertEquals(2, panel.getSelectedEntries().size());
            assertSame(alphaCard, panel.getSelectedEntries().get(0));
            assertSame(betaCard, panel.getSelectedEntries().get(1));
        });
    }
}
