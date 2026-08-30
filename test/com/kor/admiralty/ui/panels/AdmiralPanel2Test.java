/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty.ui.panels;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.kor.admiralty.AppTestFixture;
import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.RosterState;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.RuleType;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;
import com.kor.admiralty.io.GameData;

/**
 * Specifies selected-Admiral propagation across the componentized Roster screen.
 */
class AdmiralPanel2Test {

    /**
     * Prevents the transitional application holder from leaking between tests.
     */
    @AfterEach
    void resetApp() {
        AppTestFixture.reset();
    }

    /**
     * Verifies construction and later selection changes reach every child panel and its Roster view.
     */
    @Test
    void selectedAdmiralPropagatesToEveryChildPanel() throws Exception {
        Ship firstShip = ship("First Admiral Ship");
        Ship secondShip = ship("Second Admiral Ship");
        GameData gameData = GameData.builder().ships(List.of(firstShip, secondShip)).build();
        AppTestFixture.initialize(gameData);
        Admiral first = new Admiral(gameData);
        Admiral second = new Admiral(gameData);
        first.addReusableShips(List.of(firstShip), RosterState.ACTIVE);
        second.addReusableShips(List.of(secondShip), RosterState.ACTIVE);

        AtomicReference<AdmiralPanel2> panelReference = new AtomicReference<AdmiralPanel2>();
        SwingUtilities.invokeAndWait(() -> panelReference.set(new AdmiralPanel2(first)));
        AdmiralPanel2 panel = panelReference.get();

        SwingUtilities.invokeAndWait(() -> assertChildSelection(panel, first));
        SwingUtilities.invokeAndWait(() -> panel.setAdmiral(second));
        SwingUtilities.invokeAndWait(() -> assertChildSelection(panel, second));
    }

    /**
     * Asserts every child received the selection and the Roster child renders its exact Active card.
     *
     * @param panel componentized parent panel
     * @param expected selected Admiral
     */
    private static void assertChildSelection(AdmiralPanel2 panel, Admiral expected) {
        for (AdmiralUI child : panel.admiralUIs) {
            assertSame(expected, child.getAdmiral());
        }
        ShipRosterPanel rosterPanel = panel.admiralUIs.stream()
                .filter(ShipRosterPanel.class::isInstance)
                .map(ShipRosterPanel.class::cast)
                .findFirst()
                .orElseThrow();
        RosterCard renderedCard = assertInstanceOf(
                RosterCard.class,
                rosterPanel.lstActive.getModel().getElementAt(0));
        assertSame(expected.getRoster().getActiveCards().get(0), renderedCard);
    }

    /**
     * Creates canonical test Ship facts.
     *
     * @param name canonical Ship name
     * @return mutable Ship fixture for GameData construction
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
}
