/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty.ui;

import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescActiveToMaintenance;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescBest;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescDeployShips;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescNext;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescPlanAssignments;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.MsgNoShipsToDeploy;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.kor.admiralty.AppTestFixture;
import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.RosterCardKind;
import com.kor.admiralty.beans.RosterChange;
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
import com.kor.admiralty.ui.resources.Images;

/**
 * Smoke-tests the production Roster screen through its Swing-facing list
 * models.
 */
class AdmiralPanelTest {

    /**
     * Asserts a Swing model contains the exact immutable cards published by one
     * Roster view.
     *
     * @param model         Swing list model under test
     * @param expectedCards exact snapshot cards expected in visible order
     */
    private static void assertEntriesAreExactCards(ListModel<?> model, List<RosterCard> expectedCards) {
        assertEquals(expectedCards.size(), model.getSize());
        for (int index = 0; index < expectedCards.size(); index++) {
            assertInstanceOf(RosterCard.class, model.getElementAt(index));
            assertSame(expectedCards.get(index), model.getElementAt(index));
        }
    }

    /**
     * Extracts canonical Ship names from card-backed Swing entries.
     *
     * @param model card-backed list model
     * @return names in visible model order
     */
    private static List<String> entryNames(ListModel<?> model) {
        List<String> names = new ArrayList<String>();
        for (int index = 0; index < model.getSize(); index++) {
            names.add(assertInstanceOf(RosterCard.class, model.getElementAt(index)).getShip().getName());
        }
        return names;
    }

    /**
     * Finds the production movement control through its user-facing action
     * description.
     *
     * @param root        component tree to search
     * @param description action description identifying the control
     * @return matching Swing button
     * @throws AssertionError if no matching control exists
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
     * Renders one card through the production list and returns its primary Ship
     * artwork.
     *
     * @param list production Roster list
     * @param card immutable card to render
     * @return primary 64-pixel Ship icon
     * @throws AssertionError if the renderer does not expose the expected artwork
     *                        label
     */
    private static Icon renderedShipIcon(JList<RosterCard> list, RosterCard card) {
        Component rendered = list.getCellRenderer().getListCellRendererComponent(
                list,
                card,
                0,
                false,
                false);
        return primaryShipIcon(rendered);
    }

    /**
     * Finds the primary artwork label inside one rendered Admiralty card.
     *
     * @param component rendered component tree
     * @return primary Ship icon
     * @throws AssertionError if no 64-pixel artwork label exists
     */
    private static Icon primaryShipIcon(Component component) {
        if (component instanceof JLabel label) {
            if (label.getPreferredSize().width == 64 && label.getPreferredSize().height == 64) {
                return label.getIcon();
            }
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                try {
                    return primaryShipIcon(child);
                } catch (AssertionError ignored) {
                    // Continue through siblings until the renderer's primary artwork label is
                    // found.
                }
            }
        }
        throw new AssertionError("Rendered card has no primary Ship artwork label");
    }

    /**
     * Creates canonical test Ship facts with an optional Starship Trait
     * description.
     *
     * @param name          canonical Ship name
     * @param starshipTrait resolved Starship Trait description, or empty
     * @return mutable Ship fixture for GameData construction
     */
    private static Ship ship(String name, String starshipTrait) {
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
                starshipTrait);
    }

    /**
     * Prevents the transitional application holder from leaking between tests.
     */
    @AfterEach
    void resetApp() {
        AppTestFixture.reset();
    }

    /**
     * Verifies the screen renders one immutable Roster view initially and after one
     * committed movement.
     */
    @Test
    void rendersAndRefreshesTheCompleteCommittedRosterView() throws Exception {
        Ship active = ship("Active Starship Trait Ship", "<html>Active Starship Trait</html>");
        Ship maintenance = ship("Maintenance Starship Trait Ship", "<html>Maintenance Starship Trait</html>");
        Ship oneTime = ship("One-Time Ship", "");
        GameData gameData = GameData.builder()
                .ships(List.of(active, maintenance, oneTime))
                .build();
        AppTestFixture.initialize(gameData);
        Admiral admiral = new Admiral(gameData);
        admiral.addReusableShips(List.of(active), RosterState.ACTIVE);
        admiral.addReusableShips(List.of(maintenance), RosterState.MAINTENANCE);
        admiral.adjustOneTimeShipQuantity(oneTime, 2);

        AtomicReference<AdmiralPanel> panelReference = new AtomicReference<AdmiralPanel>();
        SwingUtilities.invokeAndWait(() -> panelReference.set(new AdmiralPanel(admiral)));
        AdmiralPanel panel = panelReference.get();
        RosterView initialView = admiral.getRoster();

        SwingUtilities.invokeAndWait(() -> {
            assertEntriesAreExactCards(panel.lstActive.getModel(), initialView.getActiveCards());
            assertEntriesAreExactCards(panel.lstMaintenance.getModel(), initialView.getMaintenanceCards());
            assertEntriesAreExactCards(panel.lstOneTimeShips.getModel(), initialView.getOneTimeCards());
            assertEquals(
                    List.of("Active Starship Trait Ship", "Maintenance Starship Trait Ship"),
                    entryNames(panel.lstTraits.getModel()));
            assertSame(
                    Images.getIcon(
                            oneTime.getIconName(),
                            oneTime.getFaction(),
                            oneTime.getRole(),
                            oneTime.getRarity(),
                            false),
                    renderedShipIcon(panel.lstOneTimeShips, initialView.getOneTimeCards().getFirst()));
        });

        List<RosterChange> committedChanges = new ArrayList<RosterChange>();
        admiral.addRosterChangeListener(committedChanges::add);
        SwingUtilities.invokeAndWait(() -> {
            panel.lstActive.setSelectedIndex(0);
            buttonWithDescription(panel, DescActiveToMaintenance).doClick();
        });
        assertEquals(1, committedChanges.size());
        RosterView committedView = committedChanges.getFirst().getAfter();
        assertSame(committedView, admiral.getRoster());

        SwingUtilities.invokeAndWait(() -> {
            assertEntriesAreExactCards(panel.lstActive.getModel(), committedView.getActiveCards());
            assertEntriesAreExactCards(panel.lstMaintenance.getModel(), committedView.getMaintenanceCards());
            assertEntriesAreExactCards(panel.lstOneTimeShips.getModel(), committedView.getOneTimeCards());
            assertEquals(
                    List.of("Active Starship Trait Ship", "Maintenance Starship Trait Ship"),
                    entryNames(panel.lstTraits.getModel()));
        });
    }

    /**
     * Verifies the original Swing flow solves through Admiral, displays exact card
     * identity, navigates Solutions,
     * deploys the selected One-Time Ship card, and reports a later stale rejection
     * without another Roster mutation.
     */
    @Test
    void solvesNavigatesAndDeploysTheExactDisplayedRosterCard() throws Exception {
        Ship sharedShip = ship("Shared Assignment Ship", "");
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

        AtomicReference<RecordingAdmiralPanel> panelReference = new AtomicReference<RecordingAdmiralPanel>();
        SwingUtilities.invokeAndWait(() -> panelReference.set(new RecordingAdmiralPanel(admiral)));
        RecordingAdmiralPanel panel = panelReference.get();

        SwingUtilities.invokeAndWait(() -> buttonWithDescription(panel, DescPlanAssignments).doClick());
        assertTrue(panel.solutions.size() > 1);
        assertEquals(expectedBestScore, panel.solutions.getFirst().getScore());
        RosterCard selectedCard = panel.solutions.getFirst().getRosterCards().getFirst();
        assertEquals(RosterCardKind.ONE_TIME, selectedCard.getKind());
        assertSame(sharedShip, selectedCard.getShip());
        assertTrue(hasLabel(panel.pnlAssignments[0], "(1x) Shared Assignment Ship"));

        SwingUtilities.invokeAndWait(() -> buttonWithDescription(panel, DescNext).doClick());
        assertEquals(1, panel.solutionIndex);
        SwingUtilities.invokeAndWait(() -> buttonWithDescription(panel, DescBest).doClick());
        assertEquals(0, panel.solutionIndex);
        assertTrue(hasLabel(panel.pnlAssignments[0], "(1x) Shared Assignment Ship"));

        SwingUtilities.invokeAndWait(() -> buttonWithDescription(panel, DescDeployShips).doClick());
        assertEquals(0, admiral.getRoster().getOneTimeQuantity(sharedShip));
        assertEquals(RosterState.ACTIVE, admiral.getRoster().getReusableState(sharedShip));
        assertEquals(1, admiral.getUsageCounts().get(sharedShip.getName()));
        assertEquals(0, panel.lstOneTimeShips.getModel().getSize());
        assertTrue(panel.dialogMessages.getFirst().toString().contains("One-time ship(s) assigned"));

        RosterView afterDeployment = admiral.getRoster();
        SwingUtilities.invokeAndWait(() -> buttonWithDescription(panel, DescDeployShips).doClick());
        assertSame(afterDeployment, admiral.getRoster());
        assertEquals(1, admiral.getUsageCounts().get(sharedShip.getName()));
        assertEquals(0, panel.lstOneTimeShips.getModel().getSize());
        assertTrue(panel.dialogMessages.get(1).toString().contains("Please plan again"));
    }

    /**
     * Verifies rebinding the original panel cannot deploy a Solution carrying the
     * previous Admiral's card identity.
     */
    @Test
    void switchingAdmiralsClearsPriorSolutionsAndDisplayedCards() throws Exception {
        Ship firstShip = ship("First Planned Ship", "");
        Ship secondShip = ship("Second Planned Ship", "");
        GameData gameData = GameData.builder().ships(List.of(firstShip, secondShip)).build();
        AppTestFixture.initialize(gameData);
        Admiral first = new Admiral(gameData);
        Admiral second = new Admiral(gameData);
        first.addReusableShips(List.of(firstShip), RosterState.ACTIVE);
        second.addReusableShips(List.of(secondShip), RosterState.ACTIVE);
        first.getAssignment(0).setRequiredEng(10);
        first.getAssignment(0).setRequiredTac(20);
        first.getAssignment(0).setRequiredSci(30);

        AtomicReference<RecordingAdmiralPanel> panelReference = new AtomicReference<RecordingAdmiralPanel>();
        SwingUtilities.invokeAndWait(() -> panelReference.set(new RecordingAdmiralPanel(first)));
        RecordingAdmiralPanel panel = panelReference.get();
        SwingUtilities.invokeAndWait(() -> buttonWithDescription(panel, DescPlanAssignments).doClick());
        assertTrue(hasLabel(panel.pnlAssignments[0], firstShip.getDisplayName()));

        SwingUtilities.invokeAndWait(() -> panel.setAdmiral(second));

        assertEquals(List.of(), panel.solutions);
        assertEquals(-1, panel.solutionIndex);
        assertTrue(hasLabel(panel.pnlAssignments[0], "No Ship"));
        assertFalse(panel.btnPrev.isEnabled());
        assertFalse(panel.btnBest.isEnabled());
        assertFalse(panel.btnNext.isEnabled());
        RosterView secondRoster = second.getRoster();
        SwingUtilities.invokeAndWait(() -> buttonWithDescription(panel, DescDeployShips).doClick());
        assertSame(secondRoster, second.getRoster());
        assertEquals(MsgNoShipsToDeploy, panel.dialogMessages.getFirst());
    }

    /**
     * Runs production actions while recording deployment dialogs at the Swing
     * system boundary.
     */
    private static final class RecordingAdmiralPanel extends AdmiralPanel {

        private static final long serialVersionUID = 1L;
        private final List<Object> dialogMessages = new ArrayList<Object>();

        /**
         * Creates the original generation bound to one Admiral.
         *
         * @param admiral selected Admiral
         */
        private RecordingAdmiralPanel(Admiral admiral) {
            super(admiral);
        }

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
