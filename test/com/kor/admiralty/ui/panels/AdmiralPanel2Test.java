/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty.ui.panels;

import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescBest;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescDeployShips;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescNext;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescPlanAssignments;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.kor.admiralty.AppTestFixture;
import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.RosterCardKind;
import com.kor.admiralty.beans.RosterState;
import com.kor.admiralty.beans.RosterView;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.RuleType;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;
import com.kor.admiralty.io.GameData;

/**
 * Specifies selected-Admiral propagation across the componentized Roster
 * screen.
 */
class AdmiralPanel2Test {

    /**
     * Asserts every child received the selection and the Roster child renders its
     * exact Active card.
     *
     * @param panel    componentized parent panel
     * @param expected selected Admiral
     */
    private static void assertChildSelection(AdmiralPanel2 panel, Admiral expected) {
        for (AdmiralUI child : panel.admiralUIs) {
            assertSame(expected, child.getAdmiral());
        }
        ShipRosterPanel rosterPanel = child(panel, ShipRosterPanel.class);
        RosterCard renderedCard = assertInstanceOf(
                RosterCard.class,
                rosterPanel.lstActive.getModel().getElementAt(0));
        assertSame(expected.getRoster().getActiveCards().getFirst(), renderedCard);
    }

    /**
     * Returns one componentized child with the requested role.
     *
     * @param panel     componentized Admiral panel
     * @param childType requested child type
     * @param <T>       concrete child type
     * @return matching production child
     * @throws java.util.NoSuchElementException if the child is absent
     */
    private static <T extends AdmiralUI> T child(AdmiralPanel2 panel, Class<T> childType) {
        return panel.admiralUIs.stream()
                .filter(childType::isInstance)
                .map(childType::cast)
                .findFirst()
                .orElseThrow();
    }

    /**
     * Finds a production action button by its user-facing description.
     *
     * @param root        component tree to search
     * @param description action description identifying the button
     * @return matching button
     * @throws AssertionError if no matching button exists
     */
    private static AbstractButton buttonWithDescription(Container root, String description) {
        for (Component component : root.getComponents()) {
            if (component instanceof AbstractButton button) {
                Action action = button.getAction();
                if (action != null && description.equals(action.getValue(Action.SHORT_DESCRIPTION))) {
                    return button;
                }
            }
            if (component instanceof Container container) {
                try {
                    return buttonWithDescription(container, description);
                } catch (AssertionError ignored) {
                    // Continue through sibling containers until the requested control is found.
                }
            }
        }
        throw new AssertionError("No button found with description: " + description);
    }

    /**
     * Searches a rendered Swing subtree for an exact user-visible label.
     *
     * @param root         component subtree to inspect
     * @param expectedText exact label text expected
     * @return true when the label is present
     */
    private static boolean hasLabel(Component root, String expectedText) {
        if (root instanceof JLabel label && expectedText.equals(label.getText())) {
            return true;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (hasLabel(child, expectedText)) {
                    return true;
                }
            }
        }
        return false;
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

    /**
     * Prevents the transitional application holder from leaking between tests.
     */
    @AfterEach
    void resetApp() {
        AppTestFixture.reset();
    }

    /**
     * Verifies construction and later selection changes reach every child panel and
     * its Roster view.
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
        first.getAssignment(0).setRequiredEng(10);
        first.getAssignment(0).setRequiredTac(20);
        first.getAssignment(0).setRequiredSci(30);

        AtomicReference<AdmiralPanel2> panelReference = new AtomicReference<AdmiralPanel2>();
        SwingUtilities.invokeAndWait(() -> panelReference.set(new AdmiralPanel2(first)));
        AdmiralPanel2 panel = panelReference.get();
        AssignmentSelectionPanel assignmentPanel = child(panel, AssignmentSelectionPanel.class);

        SwingUtilities.invokeAndWait(() -> assertChildSelection(panel, first));
        SwingUtilities.invokeAndWait(() -> buttonWithDescription(assignmentPanel, DescPlanAssignments).doClick());
        assertTrue(hasLabel(assignmentPanel.pnlAssignments[0], firstShip.getDisplayName()));
        SwingUtilities.invokeAndWait(() -> panel.setAdmiral(second));
        SwingUtilities.invokeAndWait(() -> assertChildSelection(panel, second));
        assertEquals(List.of(), assignmentPanel.solutions);
        assertEquals(-1, assignmentPanel.solutionIndex);
        assertTrue(hasLabel(assignmentPanel.pnlAssignments[0], "No Ship"));
        assertFalse(assignmentPanel.btnPrev.isEnabled());
        assertFalse(assignmentPanel.btnBest.isEnabled());
        assertFalse(assignmentPanel.btnNext.isEnabled());
    }

    /**
     * Verifies the componentized Swing flow solves through Admiral, displays exact
     * card identity, navigates
     * Solutions, deploys the selected One-Time Ship card, and reports a stale retry
     * without mutating the Roster.
     */
    @Test
    void componentizedFlowSolvesNavigatesAndDeploysTheExactDisplayedRosterCard() throws Exception {
        Ship sharedShip = ship("Componentized Shared Ship");
        GameData gameData = GameData.builder().ships(List.of(sharedShip)).build();
        AppTestFixture.initialize(gameData);
        Admiral admiral = new Admiral(gameData);
        admiral.addReusableShips(List.of(sharedShip), RosterState.ACTIVE);
        admiral.adjustOneTimeShipQuantity(sharedShip, 1);
        admiral.setPrioritizeActive(false);
        admiral.getAssignment(0).setRequiredEng(10);
        admiral.getAssignment(0).setRequiredTac(20);
        admiral.getAssignment(0).setRequiredSci(30);
        double expectedBestScore = admiral.solveAssignments().getFirst().getScore();

        AtomicReference<RecordingAssignmentSelectionPanel> panelReference = new AtomicReference<RecordingAssignmentSelectionPanel>();
        SwingUtilities.invokeAndWait(() -> {
            RecordingAssignmentSelectionPanel panel = new RecordingAssignmentSelectionPanel();
            panel.setAdmiral(admiral);
            panelReference.set(panel);
        });
        RecordingAssignmentSelectionPanel panel = panelReference.get();

        SwingUtilities.invokeAndWait(() -> buttonWithDescription(panel, DescPlanAssignments).doClick());
        assertTrue(panel.solutions.size() > 1);
        assertEquals(expectedBestScore, panel.solutions.getFirst().getScore());
        RosterCard selectedCard = panel.solutions.getFirst().getRosterCards().getFirst();
        assertEquals(RosterCardKind.ONE_TIME, selectedCard.getKind());
        assertSame(sharedShip, selectedCard.getShip());
        assertTrue(hasLabel(panel.pnlAssignments[0], "(1x) Componentized Shared Ship"));

        SwingUtilities.invokeAndWait(() -> buttonWithDescription(panel, DescNext).doClick());
        assertEquals(1, panel.solutionIndex);
        SwingUtilities.invokeAndWait(() -> buttonWithDescription(panel, DescBest).doClick());
        assertEquals(0, panel.solutionIndex);
        assertTrue(hasLabel(panel.pnlAssignments[0], "(1x) Componentized Shared Ship"));

        SwingUtilities.invokeAndWait(() -> buttonWithDescription(panel, DescDeployShips).doClick());
        assertEquals(0, admiral.getRoster().getOneTimeQuantity(sharedShip));
        assertEquals(RosterState.ACTIVE, admiral.getRoster().getReusableState(sharedShip));
        assertEquals(1, admiral.getUsageCounts().get(sharedShip.getName()));
        assertTrue(panel.dialogMessages.getFirst().toString().contains("One-time ship(s) assigned"));

        RosterView afterDeployment = admiral.getRoster();
        SwingUtilities.invokeAndWait(() -> buttonWithDescription(panel, DescDeployShips).doClick());
        assertSame(afterDeployment, admiral.getRoster());
        assertEquals(1, admiral.getUsageCounts().get(sharedShip.getName()));
        assertTrue(panel.dialogMessages.get(1).toString().contains("Please plan again"));
    }

    /**
     * Runs componentized production actions while recording deployment dialogs at
     * the Swing system boundary.
     */
    private static final class RecordingAssignmentSelectionPanel extends AssignmentSelectionPanel {

        private static final long serialVersionUID = 1L;
        private final List<Object> dialogMessages = new ArrayList<Object>();

        /**
         * Records dialog content without opening a native window in the headless test
         * runtime.
         */
        @Override
        protected void showMessageDialog(Object message) {
            dialogMessages.add(message);
        }
    }
}
