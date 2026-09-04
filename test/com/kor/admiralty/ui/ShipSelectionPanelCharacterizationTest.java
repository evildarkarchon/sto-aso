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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.stream.IntStream;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.enums.PlayerFaction;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.RuleType;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;
import com.kor.admiralty.ui.resources.ShipIconFactory;

/**
 * Characterizes the established reusable and One-Time Ship selection
 * presentation through its observable Swing surface.
 */
class ShipSelectionPanelCharacterizationTest {

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
     * Creates canonical Ship facts with explicit faction and tier dimensions.
     *
     * @param name    canonical Ship name
     * @param faction faction filter value
     * @param tier    tier filter value
     * @return canonical test Ship
     */
    private static Ship ship(String name, ShipFaction faction, Tier tier) {
        return new ShipImpl(
                faction,
                tier,
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
     * Supplies one same-tier Ship for every faction, including the historical
     * unclassified value.
     *
     * @return reusable selection candidates
     */
    private static List<Ship> factionCandidates() {
        return List.of(
                ship("Federation", ShipFaction.Federation, Tier.Tier6),
                ship("Klingon", ShipFaction.Klingon, Tier.Tier6),
                ship("Romulan", ShipFaction.Romulan, Tier.Tier6),
                ship("JemHadar", ShipFaction.JemHadar, Tier.Tier6),
                ship("Universal", ShipFaction.Universal, Tier.Tier6),
                ship("Historical Faction", ShipFaction.None, Tier.Tier6));
    }

    /**
     * Returns the established visible names for one Admiral faction profile.
     *
     * @param faction Admiral faction
     * @return canonical names in visible order
     */
    private static List<String> expectedProfile(PlayerFaction faction) {
        return switch (faction) {
            case Federation, JemHadarFed -> List.of(
                    "Federation", "Historical Faction", "JemHadar", "Universal");
            case Klingon, JemHadarKDF -> List.of(
                    "Historical Faction", "JemHadar", "Klingon", "Universal");
            case RomulanFed -> List.of(
                    "Federation", "Historical Faction", "JemHadar", "Romulan", "Universal");
            case RomulanKDF -> List.of(
                    "Historical Faction", "JemHadar", "Klingon", "Romulan", "Universal");
        };
    }

    /**
     * Returns visible canonical names through the selection list's public model.
     *
     * @param panel configured selection presentation
     * @return visible names in presentation order
     */
    private static List<String> visibleNames(ShipSelectionPanel panel) {
        JList<?> list = child(panel, JList.class);
        return IntStream.range(0, list.getModel().getSize())
                .mapToObj(index -> ((Ship) list.getModel().getElementAt(index)).getName())
                .toList();
    }

    /**
     * Finds the first component of one type in a Swing subtree.
     *
     * @param root          subtree to inspect
     * @param componentType requested component type
     * @param <T>           concrete component type
     * @return first matching component
     */
    private static <T extends Component> T child(Container root, Class<T> componentType) {
        for (Component component : root.getComponents()) {
            if (componentType.isInstance(component)) {
                return componentType.cast(component);
            }
            if (component instanceof Container container) {
                try {
                    return child(container, componentType);
                } catch (java.util.NoSuchElementException ignored) {
                    // Continue through sibling components until the requested child is found.
                }
            }
        }
        throw new java.util.NoSuchElementException(componentType.getName());
    }

    /**
     * Reports whether a rendered component subtree contains one exact visible
     * label.
     *
     * @param root         subtree to inspect
     * @param expectedText exact label text
     * @return whether the text is currently visible in the component tree
     */
    private static boolean hasLabel(Component root, String expectedText) {
        if (root instanceof JLabel label && expectedText.equals(label.getText())) {
            return true;
        }
        if (root instanceof Container container) {
            for (Component component : container.getComponents()) {
                if (hasLabel(component, expectedText)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Verifies the selection surface remains constructible in the configured
     * headless test environment so its visible behavior can be characterized.
     */
    @Test
    void selectionSurfaceCanBeExercisedHeadlessly() {
        assertDoesNotThrow(() -> new ShipSelectionPanel(testIconRenderer()));
    }

    /**
     * Verifies the reusable dialog path installs every established Admiral faction
     * profile while preserving historical unclassified Ships.
     *
     * @param faction Admiral faction supplied by the workspace
     * @throws Exception if event-thread dispatch fails
     */
    @ParameterizedTest
    @EnumSource(PlayerFaction.class)
    void reusableDialogUsesTheEstablishedAdmiralFactionProfile(PlayerFaction faction) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ShipSelectionPanel panel = ShipSelectionPanel.activeShips(
                    faction,
                    factionCandidates(),
                    testIconRenderer());

            assertEquals(expectedProfile(faction), visibleNames(panel));
        });
    }

    /**
     * Verifies the One-Time dialog applies its faction and Tier 6 restrictions
     * together, keeps historical tiers, rejects Small Craft, and deduplicates
     * canonical candidates.
     *
     * @param faction Admiral faction supplied by the workspace
     * @throws Exception if event-thread dispatch fails
     */
    @ParameterizedTest
    @EnumSource(PlayerFaction.class)
    void oneTimeDialogCombinesFactionAndTierSixProfiles(PlayerFaction faction) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<Ship> candidates = new java.util.ArrayList<Ship>(factionCandidates());
            candidates.add(factionCandidates().getFirst());
            candidates.add(ship("Historical Tier", ShipFaction.Universal, Tier.None));
            candidates.add(ship("Excluded Small Craft", ShipFaction.Universal, Tier.SmallCraft));
            candidates.add(ship("Excluded Tier 5", ShipFaction.Universal, Tier.Tier5));

            ShipSelectionPanel panel = ShipSelectionPanel.oneTimeShips(
                    faction,
                    candidates,
                    testIconRenderer());

            List<String> expected = new java.util.ArrayList<String>();
            expected.add("Historical Tier");
            expected.addAll(expectedProfile(faction));
            assertEquals(expected, visibleNames(panel));
        });
    }

    /**
     * Verifies Ship selection remains multiple-interval, reports entries in
     * visible order, and keeps Ship details synchronized with the current
     * selection.
     *
     * @throws Exception if event-thread dispatch fails
     */
    @Test
    void multipleSelectionReturnsVisibleOrderAndDrivesShipDetails() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Ship alpha = ship("Alpha", ShipFaction.Federation, Tier.Tier6);
            Ship beta = ship("Beta", ShipFaction.Federation, Tier.Tier6);
            Ship gamma = ship("Gamma", ShipFaction.Federation, Tier.Tier6);
            ShipSelectionPanel panel = ShipSelectionPanel.activeShips(
                    PlayerFaction.Federation,
                    List.of(gamma, alpha, beta),
                    testIconRenderer());
            JList<?> list = child(panel, JList.class);

            list.setSelectedIndex(1);
            assertSame(beta, list.getSelectedValue());
            assertTrue(hasLabel(panel, "Beta"));
            list.setSelectedIndices(new int[]{2, 0});

            assertEquals(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION, list.getSelectionMode());
            assertEquals(List.of(alpha, gamma), panel.getSelectedShips());

            list.clearSelection();
            assertFalse(hasLabel(panel, "Beta"));
        });
    }
}
