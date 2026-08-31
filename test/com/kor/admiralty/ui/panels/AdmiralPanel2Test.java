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
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescNumAssignments;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescPlanAssignments;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kor.admiralty.AppTestFixture;
import com.kor.admiralty.beans.AdmAssignment;
import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.Event;
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
import com.kor.admiralty.ui.AssignmentPanel;
import com.kor.admiralty.ui.resources.ShipIconFactory;

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
     * Collects every component of one type from a Swing subtree in display order.
     *
     * @param root component subtree to inspect
     * @param componentType requested Swing component type
     * @param <T> concrete component type
     * @return matching components in depth-first order
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
     * Finds a combo box containing one exact GameData entry.
     *
     * @param root Assignment editor subtree
     * @param expectedItem supplied reference-data entry
     * @return combo box exposing the entry
     * @throws AssertionError if no combo contains the entry
     */
    private static JComboBox<?> comboContaining(Container root, Object expectedItem) {
        for (JComboBox<?> combo : components(root, JComboBox.class)) {
            for (int index = 0; index < combo.getItemCount(); index++) {
                if (combo.getItemAt(index) == expectedItem) {
                    return combo;
                }
            }
        }
        throw new AssertionError("No combo contains supplied GameData entry: " + expectedItem);
    }

    /**
     * Finds one manual numeric field by its stable GridBag position in Assignment
     * statistics.
     *
     * @param root Assignment editor subtree
     * @param gridx field column
     * @param gridy field row
     * @return matching editable field
     * @throws AssertionError if the field is absent
     */
    private static JFormattedTextField formattedFieldAt(Container root, int gridx, int gridy) {
        for (JFormattedTextField field : components(root, JFormattedTextField.class)) {
            Container parent = field.getParent();
            if (parent.getLayout() instanceof GridBagLayout layout) {
                GridBagConstraints constraints = layout.getConstraints(field);
                if (constraints.gridx == gridx && constraints.gridy == gridy) {
                    return field;
                }
            }
        }
        throw new AssertionError("No formatted field at " + gridx + "," + gridy);
    }

    /**
     * Renders one Roster card through its production list renderer.
     *
     * @param list production Roster list
     * @param card immutable card to render
     * @return rendered component subtree
     */
    private static Component renderCard(JList<RosterCard> list, RosterCard card) {
        return list.getCellRenderer().getListCellRendererComponent(list, card, 0, false, false);
    }

    /**
     * Finds the primary 64-pixel Ship artwork inside a rendered card.
     *
     * @param component rendered card subtree
     * @return primary Ship artwork
     * @throws AssertionError if the artwork label is absent
     */
    private static Icon primaryShipIcon(Component component) {
        if (component instanceof JLabel label
                && label.getPreferredSize().width == 64
                && label.getPreferredSize().height == 64) {
            return label.getIcon();
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                try {
                    return primaryShipIcon(child);
                } catch (AssertionError ignored) {
                    // Continue through sibling components until the artwork label is found.
                }
            }
        }
        throw new AssertionError("Rendered card has no primary Ship artwork label");
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
     * Creates deterministic Ship artwork without application bootstrap, Icon Cache
     * state, or remote acquisition.
     *
     * @return isolated test icon-rendering adapter
     */
    private static ShipIconFactory testIconRenderer() {
        return (iconName, faction, role, rarity, owned) ->
                new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
    }

    /**
     * Makes application-global state unavailable before every root-seam test.
     */
    @BeforeEach
    void resetApp() {
        AppTestFixture.reset();
    }

    /**
     * Verifies construction and later selection changes reach every child panel and
     * its Roster view.
     *
     * @throws Exception if Swing event-thread dispatch fails
     */
    @Test
    void selectedAdmiralPropagatesToEveryChildPanel() throws Exception {
        Ship firstShip = ship("First Admiral Ship");
        Ship secondShip = ship("Second Admiral Ship");
        GameData gameData = GameData.builder().ships(List.of(firstShip, secondShip)).build();
        ShipIconFactory iconRenderer = testIconRenderer();
        Admiral first = new Admiral(gameData);
        Admiral second = new Admiral(gameData);
        first.addReusableShips(List.of(firstShip), RosterState.ACTIVE);
        second.addReusableShips(List.of(secondShip), RosterState.ACTIVE);
        first.getAssignment(0).setRequiredEng(10);
        first.getAssignment(0).setRequiredTac(20);
        first.getAssignment(0).setRequiredSci(30);

        AtomicReference<AdmiralPanel2> panelReference = new AtomicReference<AdmiralPanel2>();
        SwingUtilities.invokeAndWait(() -> panelReference.set(new AdmiralPanel2(first, gameData, iconRenderer)));
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
     * Verifies reusable, One-Time, and Starship Trait cards retain exact visual
     * facts and distinct text through the root-supplied test rendering adapter.
     *
     * @throws Exception if Swing event-thread dispatch fails
     */
    @Test
    void rosterCardsUseTheSuppliedIconRendererWithoutApplicationState() throws Exception {
        Ship ship = new ShipImpl(
                ShipFaction.Romulan,
                Tier.Tier6,
                Rarity.UltraRare,
                Role.Sci,
                "Explicit Presentation Ship",
                10,
                20,
                30,
                RuleType.All.rewardBonus(0),
                "<html>Explicit Trait</html>");
        GameData gameData = GameData.builder().ships(List.of(ship)).build();
        Admiral admiral = new Admiral(gameData);
        admiral.addReusableShips(List.of(ship), RosterState.ACTIVE);
        admiral.adjustOneTimeShipQuantity(ship, 1);
        RecordingIconRenderer iconRenderer = new RecordingIconRenderer();

        AtomicReference<AdmiralPanel2> panelReference = new AtomicReference<AdmiralPanel2>();
        SwingUtilities.invokeAndWait(
                () -> panelReference.set(new AdmiralPanel2(admiral, gameData, iconRenderer)));
        AdmiralPanel2 panel = panelReference.get();
        ShipRosterPanel rosterPanel = child(panel, ShipRosterPanel.class);
        OneTimeShipPanel oneTimePanel = child(panel, OneTimeShipPanel.class);
        StarshipTraitsPanel traitsPanel = child(panel, StarshipTraitsPanel.class);

        SwingUtilities.invokeAndWait(() -> {
            RosterCard reusable = admiral.getRoster().getActiveCards().getFirst();
            RosterCard oneTime = admiral.getRoster().getOneTimeCards().getFirst();
            Component reusableCard = renderCard(rosterPanel.lstActive, reusable);
            Component oneTimeCard = renderCard(oneTimePanel.uiList, oneTime);
            Component traitCard = renderCard(traitsPanel.uiList, reusable);

            assertAll(
                    () -> assertSame(iconRenderer.icon, primaryShipIcon(reusableCard)),
                    () -> assertSame(iconRenderer.icon, primaryShipIcon(oneTimeCard)),
                    () -> assertSame(iconRenderer.icon, primaryShipIcon(traitCard)),
                    () -> assertTrue(hasLabel(reusableCard, ship.getDisplayName())),
                    () -> assertTrue(hasLabel(oneTimeCard, "(1x) " + ship.getName())));
        });

        IconRequest reusableRequest = new IconRequest(
                ship.getIconName(),
                ShipFaction.Romulan,
                Role.Sci,
                Rarity.UltraRare,
                true);
        assertEquals(
                List.of(
                        reusableRequest,
                        new IconRequest(
                                ship.getIconName(),
                                ShipFaction.Romulan,
                                Role.Sci,
                                Rarity.UltraRare,
                                false),
                        reusableRequest),
                iconRenderer.requests);
    }

    /**
     * Verifies Assignment and Event lookup, manual value entry, and the
     * one-to-three-Assignment control all operate from root-supplied GameData on the
     * Swing event thread.
     *
     * @throws Exception if GameData loading or Swing event-thread dispatch fails
     */
    @Test
    void assignmentInteractionsUseTheSuppliedGameDataAndPreserveManualEntry() throws Exception {
        GameData gameData = GameData.load(Path.of("test", "resources", "gamedata"));
        Admiral admiral = new Admiral(gameData);
        AdmAssignment assignmentChoice = gameData.assignments().iterator().next();
        Event eventChoice = gameData.events().iterator().next();

        AtomicReference<AdmiralPanel2> panelReference = new AtomicReference<AdmiralPanel2>();
        SwingUtilities.invokeAndWait(
                () -> panelReference.set(new AdmiralPanel2(admiral, gameData, testIconRenderer())));
        AdmiralPanel2 panel = panelReference.get();
        AssignmentSelectionPanel assignments = child(panel, AssignmentSelectionPanel.class);
        AssignmentPanel firstAssignment = assignments.pnlAssignments[0];

        SwingUtilities.invokeAndWait(() -> {
            comboContaining(firstAssignment, assignmentChoice).setSelectedItem(assignmentChoice);
            comboContaining(firstAssignment, eventChoice).setSelectedItem(eventChoice);
            formattedFieldAt(firstAssignment, 1, 3).setValue(77);

            for (AbstractButton button : components(assignments, AbstractButton.class)) {
                Action action = button.getAction();
                if (action != null
                        && DescNumAssignments.equals(action.getValue(Action.SHORT_DESCRIPTION))
                        && "3".equals(action.getValue(Action.NAME))) {
                    button.doClick();
                }
            }
        });

        assertAll(
                () -> assertEquals(77, admiral.getAssignment(0).getRequiredEng()),
                () -> assertEquals(assignmentChoice.getTac(), admiral.getAssignment(0).getRequiredTac()),
                () -> assertEquals(assignmentChoice.getSci(), admiral.getAssignment(0).getRequiredSci()),
                () -> assertEquals(eventChoice.getEng(), admiral.getAssignment(0).getEventEng()),
                () -> assertEquals(eventChoice.getTac(), admiral.getAssignment(0).getEventTac()),
                () -> assertEquals(eventChoice.getSci(), admiral.getAssignment(0).getEventSci()),
                () -> assertEquals(eventChoice.getCritRate(), admiral.getAssignment(0).getEventCritRate()),
                () -> assertEquals(3, admiral.getAssignmentCount()),
                () -> assertTrue(assignments.pnlAssignments[0].isVisible()),
                () -> assertTrue(assignments.pnlAssignments[1].isVisible()),
                () -> assertTrue(assignments.pnlAssignments[2].isVisible()));
    }

    /**
     * Verifies the componentized Swing flow solves through Admiral, displays exact
     * card identity, navigates
     * Solutions, deploys the selected One-Time Ship card, and reports a stale retry
     * without mutating the Roster.
     *
     * @throws Exception if Swing event-thread dispatch fails
     */
    @Test
    void componentizedFlowSolvesNavigatesAndDeploysTheExactDisplayedRosterCard() throws Exception {
        Ship sharedShip = ship("Componentized Shared Ship");
        GameData gameData = GameData.builder().ships(List.of(sharedShip)).build();
        ShipIconFactory iconRenderer = testIconRenderer();
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
            RecordingAssignmentSelectionPanel panel = new RecordingAssignmentSelectionPanel(gameData, iconRenderer);
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
         * Creates the recording test boundary over the same explicit production seam.
         *
         * @param gameData Assignment and Event reference data
         * @param iconRenderer test Ship artwork adapter
         */
        private RecordingAssignmentSelectionPanel(GameData gameData, ShipIconFactory iconRenderer) {
            super(gameData, iconRenderer);
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

    /** Captures one Ship icon-rendering request at the explicit workspace boundary. */
    private record IconRequest(
            String iconName,
            ShipFaction faction,
            Role role,
            Rarity rarity,
            boolean owned) {
    }

    /** Records icon facts while returning deterministic in-memory artwork. */
    private static final class RecordingIconRenderer implements ShipIconFactory {

        private final ImageIcon icon = new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        private final List<IconRequest> requests = new ArrayList<IconRequest>();

        /** Records exact presentation facts and returns the isolated test icon. */
        @Override
        public ImageIcon getIcon(String iconName, ShipFaction faction, Role role, Rarity rarity, boolean owned) {
            requests.add(new IconRequest(iconName, faction, role, rarity, owned));
            return icon;
        }
    }
}
