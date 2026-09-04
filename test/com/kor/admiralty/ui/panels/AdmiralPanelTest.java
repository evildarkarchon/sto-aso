/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty.ui.panels;

import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescActiveToMaintenance;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescAddActiveShips;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescAddOneTimeShips;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescAllActiveToMaintenance;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescAllMaintenanceToActive;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescBest;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescClearAssignments;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescDeployShips;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescExportShips;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescImportShips;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescMaintenanceToActive;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescNext;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescNumAssignments;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescPlanAssignments;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescPrev;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescRemoveActiveShips;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.DescRemoveOneTimeShips;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.LabelExportShips;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.LabelImportShips;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.MsgExportFailed;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.MsgExportSuccessful;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.MsgImportFailed;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.MsgImportSuccessful;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.MsgNoImport;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.MsgNoShipsToDeploy;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.MsgNoSolution;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.TitleExportShips;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.TitleImportShips;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kor.admiralty.AppTestFixture;
import com.kor.admiralty.beans.AdmAssignment;
import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.Assignment;
import com.kor.admiralty.beans.Event;
import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.RosterCardKind;
import com.kor.admiralty.beans.RosterChangeListener;
import com.kor.admiralty.beans.RosterState;
import com.kor.admiralty.beans.RosterView;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.enums.PlayerFaction;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.RuleType;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.ShipPriority;
import com.kor.admiralty.enums.Tier;
import com.kor.admiralty.io.AdmiralsStore;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.ui.AssignmentPanel;
import com.kor.admiralty.ui.resources.Images;
import com.kor.admiralty.ui.resources.ShipIconFactory;
import com.kor.admiralty.ui.util.TextFileFilter;

/**
 * Specifies the lifetime-bound Admiral workspace through its production root seam.
 */
class AdmiralPanelTest {

    @TempDir
    Path tempDir;

    /**
     * Returns one componentized child with the requested role.
     *
     * @param root      component tree to search
     * @param childType requested child type
     * @param <T>       concrete child type
     * @return matching production child
     * @throws java.util.NoSuchElementException if the child is absent
     */
    private static <T extends Component> T child(Container root, Class<T> childType) {
        for (Component component : root.getComponents()) {
            if (childType.isInstance(component)) {
                return childType.cast(component);
            }
            if (component instanceof Container container) {
                try {
                    return child(container, childType);
                } catch (java.util.NoSuchElementException ignored) {
                    // Continue through sibling containers until the requested child is found.
                }
            }
        }
        throw new java.util.NoSuchElementException(childType.getName());
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
     * @param root          component subtree to inspect
     * @param componentType requested Swing component type
     * @param <T>           concrete component type
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
     * @param root         Assignment editor subtree
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
     * @param root  Assignment editor subtree
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
        return (iconName, faction, role, rarity,
                owned) -> new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
    }

    /** Selects the exact displayed Roster card for one Ship. */
    private static void selectShip(JList<RosterCard> list, Ship ship) {
        for (int index = 0; index < list.getModel().getSize(); index++) {
            if (list.getModel().getElementAt(index).getShip() == ship) {
                list.setSelectedIndex(index);
                return;
            }
        }
        throw new AssertionError("Missing displayed Ship: " + ship.getName());
    }

    /**
     * Delivers a double-click to one visible card row without opening a native
     * window.
     *
     * @param list  production Roster list
     * @param index visible card index to activate
     */
    private static void doubleClick(JList<RosterCard> list, int index) {
        list.setFixedCellHeight(76);
        list.setSize(320, 240);
        MouseEvent event = new MouseEvent(
                list,
                MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                0,
                1,
                index * 76 + 1,
                2,
                false,
                MouseEvent.BUTTON1);
        list.dispatchEvent(event);
    }

    /**
     * Constructs the production root with explicit test adapters on the Swing
     * event thread.
     *
     * @param admiral fixed Admiral for the workspace lifetime
     * @param gameData reference data supplied to every child panel
     * @param admiralsStore persistence module used by Roster transfer
     * @param iconRenderer deterministic Ship artwork adapter
     * @param fileDialog Roster import/export dialog adapter
     * @param messageDialog Assignment/deployment message adapter
     * @param selectionDialog Roster Ship-selection adapter
     * @return constructed production root
     * @throws Exception if event-thread dispatch fails
     */
    private AdmiralPanel createRootOnEventThread(
            Admiral admiral,
            GameData gameData,
            AdmiralsStore admiralsStore,
            ShipIconFactory iconRenderer,
            ShipRosterPanel.RosterFileDialog fileDialog,
            AssignmentSelectionPanel.MessageDialog messageDialog,
            RosterSelectionDialog selectionDialog) throws Exception {
        AtomicReference<AdmiralPanel> rootReference = new AtomicReference<AdmiralPanel>();
        SwingUtilities.invokeAndWait(() -> rootReference.set(new AdmiralPanel(
                admiral,
                gameData,
                admiralsStore,
                tempDir,
                iconRenderer,
                fileDialog,
                messageDialog,
                selectionDialog)));
        return rootReference.get();
    }

    /**
     * Makes application-global state unavailable before every root-seam test.
     */
    @BeforeEach
    void resetApp() {
        AppTestFixture.reset();
    }

    /**
     * Verifies the root exposes no Admiral lookup or rebinding operation and
     * remains
     * isolated from changes to another Admiral after construction.
     *
     * @throws Exception if Swing event-thread dispatch or reflection fails
     */
    @Test
    void workspaceIsPermanentlyBoundToConstructionTimeAdmiral() throws Exception {
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
        AdmiralsStore admiralsStore = new AdmiralsStore();

        AtomicReference<AdmiralPanel> panelReference = new AtomicReference<AdmiralPanel>();
        SwingUtilities.invokeAndWait(() -> panelReference.set(
                new AdmiralPanel(first, gameData, admiralsStore, tempDir, iconRenderer)));
        AdmiralPanel panel = panelReference.get();
        ShipRosterPanel rosterPanel = child(panel, ShipRosterPanel.class);
        AssignmentSelectionPanel assignmentPanel = child(panel, AssignmentSelectionPanel.class);

        SwingUtilities.invokeAndWait(() -> buttonWithDescription(assignmentPanel, DescPlanAssignments).doClick());
        assertTrue(hasLabel(assignmentPanel.pnlAssignments[0], firstShip.getDisplayName()));
        SwingUtilities.invokeAndWait(() -> second.moveReusableCards(
                second.getRoster().getActiveCards(),
                RosterState.MAINTENANCE));

        RosterCard renderedCard = assertInstanceOf(
                RosterCard.class,
                rosterPanel.lstActive.getModel().getElementAt(0));
        assertAll(
                () -> assertSame(first.getRoster().getActiveCards().getFirst(), renderedCard),
                () -> assertTrue(hasLabel(assignmentPanel.pnlAssignments[0], firstShip.getDisplayName())),
                () -> assertThrows(NoSuchMethodException.class, () -> AdmiralPanel.class.getMethod("getAdmiral")),
                () -> assertThrows(
                        NoSuchMethodException.class,
                        () -> AdmiralPanel.class.getMethod("setAdmiral", Admiral.class)));
    }

    /**
     * Verifies the visible name, faction, and Ship-priority controls report user
     * intent to the fixed Admiral and retain the committed values.
     *
     * @throws Exception if persistence initialization or event-thread dispatch fails
     */
    @Test
    void identityControlsUpdateTheFixedAdmiral() throws Exception {
        GameData gameData = GameData.builder().build();
        Admiral admiral = new Admiral(gameData);
        admiral.setName("Initial Admiral");
        AdmiralPanel[] root = new AdmiralPanel[1];
        AdmiralsStore admiralsStore = new AdmiralsStore();

        SwingUtilities.invokeAndWait(() -> root[0] = new AdmiralPanel(
                admiral,
                gameData,
                admiralsStore,
                tempDir,
                testIconRenderer()));

        assertAll(
                () -> assertEquals("Initial Admiral", root[0].txtName.getText()),
                () -> assertEquals(PlayerFaction.Federation, root[0].cbxFaction.getSelectedItem()),
                () -> assertEquals(ShipPriority.Active, root[0].cbxShipPriority.getSelectedItem()));

        SwingUtilities.invokeAndWait(() -> {
            root[0].txtName.setText("Edited Admiral");
            root[0].cbxFaction.setSelectedItem(PlayerFaction.RomulanFed);
            root[0].cbxShipPriority.setSelectedItem(ShipPriority.OneTime);
        });

        assertAll(
                () -> assertEquals("Edited Admiral", admiral.getName()),
                () -> assertEquals(PlayerFaction.RomulanFed, admiral.getFaction()),
                () -> assertFalse(admiral.getPrioritizeActive()),
                () -> assertEquals(ShipPriority.OneTime, root[0].cbxShipPriority.getSelectedItem()));
    }

    /**
     * Verifies the root owns exactly one subscription of each kind, releases both
     * once, and cannot be updated by its Admiral after idempotent disposal.
     *
     * @throws Exception if Swing event-thread dispatch fails
     */
    @Test
    void disposalReleasesSoleListenerOwnershipExactlyOnce() throws Exception {
        Ship initialShip = ship("Initial Bound Ship");
        Ship laterShip = ship("Later Bound Ship");
        GameData gameData = GameData.builder().ships(List.of(initialShip, laterShip)).build();
        TrackingAdmiral admiral = new TrackingAdmiral(gameData);
        admiral.setName("Bound Admiral");
        admiral.addReusableShips(List.of(initialShip), RosterState.ACTIVE);
        AdmiralsStore admiralsStore = new AdmiralsStore();

        AtomicReference<AdmiralPanel> panelReference = new AtomicReference<AdmiralPanel>();
        SwingUtilities.invokeAndWait(() -> panelReference.set(new AdmiralPanel(
                admiral,
                gameData,
                admiralsStore,
                tempDir,
                testIconRenderer())));
        AdmiralPanel panel = panelReference.get();
        ShipRosterPanel rosterPanel = child(panel, ShipRosterPanel.class);
        AssignmentSelectionPanel assignmentPanel = child(panel, AssignmentSelectionPanel.class);
        JFormattedTextField assignmentEng = formattedFieldAt(assignmentPanel.pnlAssignments[0], 1, 3);

        assertAll(
                () -> assertEquals(1, admiral.propertyListenerAdds),
                () -> assertEquals(1, admiral.rosterListenerAdds));

        SwingUtilities.invokeAndWait(() -> {
            panel.dispose();
            panel.dispose();
            admiral.setName("Changed After Disposal");
            admiral.addReusableShips(List.of(laterShip), RosterState.ACTIVE);
            admiral.getAssignment(0).setRequiredEng(77);
        });

        assertAll(
                () -> assertEquals(1, admiral.propertyListenerRemoves),
                () -> assertEquals(1, admiral.rosterListenerRemoves),
                () -> assertEquals("Bound Admiral", panel.txtName.getText()),
                () -> assertEquals(1, rosterPanel.lstActive.getModel().getSize()),
                () -> assertEquals(0, ((Number) assignmentEng.getValue()).intValue()),
                () -> assertSame(
                        initialShip,
                        rosterPanel.lstActive.getModel().getElementAt(0).getShip()));
    }

    /**
     * Verifies manual Assignment edits are reported through the root while the
     * nested editor retains no mutable Assignment binding.
     *
     * @throws Exception if Swing event-thread dispatch fails
     */
    @Test
    void manualAssignmentIntentFlowsThroughRootWithoutMutableChildBinding() throws Exception {
        GameData gameData = GameData.builder().build();
        TrackingAdmiral admiral = new TrackingAdmiral(gameData);
        AdmiralsStore admiralsStore = new AdmiralsStore();
        AtomicReference<AdmiralPanel> rootReference = new AtomicReference<AdmiralPanel>();

        SwingUtilities.invokeAndWait(() -> rootReference.set(new AdmiralPanel(
                admiral,
                gameData,
                admiralsStore,
                tempDir,
                testIconRenderer())));
        AssignmentSelectionPanel assignments = child(rootReference.get(), AssignmentSelectionPanel.class);
        AssignmentPanel editor = assignments.pnlAssignments[0];
        admiral.assignmentLookups = 0;

        SwingUtilities.invokeAndWait(() -> {
            assertNull(editor.getAssignment());
            formattedFieldAt(editor, 1, 3).setValue(42);
        });

        assertAll(
                () -> assertEquals(1, admiral.assignmentLookups),
                () -> assertEquals(42, admiral.assignmentAt(0).getRequiredEng()));
    }

    /**
     * Verifies one committed Roster change fans the exact same immutable
     * post-commit
     * view to Primary, One-Time Ships, Assignment/Solution, and Starship Traits.
     *
     * @throws Exception if Swing event-thread dispatch fails
     */
    @Test
    void oneCommittedRosterChangeProjectsOneCoherentViewAcrossAllChildren() throws Exception {
        Ship traitShip = new ShipImpl(
                ShipFaction.Federation,
                Tier.Tier6,
                Rarity.Epic,
                Role.Tac,
                "Coherent Trait Ship",
                10,
                20,
                30,
                RuleType.All.rewardBonus(0),
                "Coherent Starship Trait");
        Ship oneTimeShip = ship("Coherent One-Time Ship");
        GameData gameData = GameData.builder().ships(List.of(traitShip, oneTimeShip)).build();
        Admiral admiral = new Admiral(gameData);
        admiral.addReusableShips(List.of(traitShip), RosterState.ACTIVE);
        admiral.adjustOneTimeShipQuantity(oneTimeShip, 1);
        RosterCard activeCard = admiral.getRoster().getActiveCards().getFirst();
        AdmiralsStore admiralsStore = new AdmiralsStore();
        AtomicReference<AdmiralPanel> rootReference = new AtomicReference<AdmiralPanel>();
        AtomicReference<RosterView> committedView = new AtomicReference<RosterView>();
        admiral.addRosterChangeListener(change -> committedView.set(change.getAfter()));

        SwingUtilities.invokeAndWait(() -> rootReference.set(new AdmiralPanel(
                admiral,
                gameData,
                admiralsStore,
                tempDir,
                testIconRenderer())));
        AdmiralPanel root = rootReference.get();
        ShipRosterPanel primary = child(root, ShipRosterPanel.class);
        OneTimeShipPanel oneTime = child(root, OneTimeShipPanel.class);
        AssignmentSelectionPanel assignments = child(root, AssignmentSelectionPanel.class);
        StarshipTraitsPanel traits = child(root, StarshipTraitsPanel.class);

        SwingUtilities.invokeAndWait(() -> admiral.moveReusableCards(
                List.of(activeCard),
                RosterState.MAINTENANCE));
        RosterView after = committedView.get();

        assertAll(
                () -> assertSame(after, primary.rosterView),
                () -> assertSame(after, oneTime.rosterView),
                () -> assertSame(after, assignments.rosterView),
                () -> assertSame(after, traits.rosterView),
                () -> assertEquals(0, primary.lstActive.getModel().getSize()),
                () -> assertSame(
                        after.getMaintenanceCards().getFirst(),
                        primary.lstMaintenance.getModel().getElementAt(0)),
                () -> assertSame(
                        after.getOneTimeCards().getFirst(),
                        oneTime.uiList.getModel().getElementAt(0)),
                () -> assertSame(
                        after.getReusableCards().getFirst(),
                        traits.uiList.getModel().getElementAt(0)));
    }

    /**
     * Verifies every reusable and One-Time Ship action crosses the production root
     * and preserves selected, bulk, quantity, and removal semantics.
     *
     * @throws Exception if Swing event-thread dispatch fails
     */
    @Test
    void rosterControlsExerciseReusableAndOneTimeShipFlowsThroughTheRoot() throws Exception {
        Ship activeShip = ship("Active Flow Ship");
        Ship maintenanceShip = ship("Maintenance Flow Ship");
        Ship addedReusableShip = ship("Added Reusable Flow Ship");
        Ship oneTimeShip = ship("One-Time Flow Ship");
        GameData gameData = GameData.builder()
                .ships(List.of(activeShip, maintenanceShip, addedReusableShip, oneTimeShip))
                .build();
        Admiral admiral = new Admiral(gameData);
        admiral.addReusableShips(List.of(activeShip), RosterState.ACTIVE);
        admiral.addReusableShips(List.of(maintenanceShip), RosterState.MAINTENANCE);
        AdmiralsStore admiralsStore = new AdmiralsStore();
        RecordingRosterSelectionDialog selectionDialog = new RecordingRosterSelectionDialog(
                addedReusableShip,
                oneTimeShip);
        AdmiralPanel root = createRootOnEventThread(
                admiral,
                gameData,
                admiralsStore,
                testIconRenderer(),
                ShipRosterPanel.RosterFileDialog.swing(),
                AssignmentSelectionPanel.MessageDialog.swing(),
                selectionDialog);
        ShipRosterPanel reusable = child(root, ShipRosterPanel.class);
        OneTimeShipPanel oneTime = child(root, OneTimeShipPanel.class);

        SwingUtilities.invokeAndWait(() -> {
            selectShip(reusable.lstActive, activeShip);
            buttonWithDescription(root, DescActiveToMaintenance).doClick();
            assertEquals(RosterState.MAINTENANCE, admiral.getRoster().getReusableState(activeShip));

            selectShip(reusable.lstMaintenance, activeShip);
            buttonWithDescription(root, DescMaintenanceToActive).doClick();
            assertEquals(RosterState.ACTIVE, admiral.getRoster().getReusableState(activeShip));

            buttonWithDescription(root, DescAllActiveToMaintenance).doClick();
            assertEquals(2, admiral.getRoster().getMaintenanceCards().size());
            buttonWithDescription(root, DescAllMaintenanceToActive).doClick();
            assertEquals(2, admiral.getRoster().getActiveCards().size());

            buttonWithDescription(root, DescAddActiveShips).doClick();
            assertEquals(RosterState.ACTIVE, admiral.getRoster().getReusableState(addedReusableShip));
            buttonWithDescription(root, DescRemoveActiveShips).doClick();
            assertEquals(RosterState.ABSENT, admiral.getRoster().getReusableState(addedReusableShip));

            buttonWithDescription(root, DescAddOneTimeShips).doClick();
            assertEquals(2, admiral.getRoster().getOneTimeQuantity(oneTimeShip));
            buttonWithDescription(root, DescRemoveOneTimeShips).doClick();
            assertEquals(1, admiral.getRoster().getOneTimeQuantity(oneTimeShip));
            buttonWithDescription(root, DescRemoveOneTimeShips).doClick();
            assertEquals(0, admiral.getRoster().getOneTimeQuantity(oneTimeShip));
            assertEquals(0, oneTime.uiList.getModel().getSize());
        });

        assertAll(
                () -> assertEquals(1, selectionDialog.reusableAdditions),
                () -> assertEquals(1, selectionDialog.reusableRemovals),
                () -> assertEquals(1, selectionDialog.oneTimeAdditions),
                () -> assertEquals(2, selectionDialog.oneTimeRemovals));
    }

    /**
     * Verifies two simultaneous roots keep Roster, Assignment, retained Solution,
     * and Starship Trait state isolated by construction-time Admiral identity.
     *
     * @throws Exception if Swing event-thread dispatch fails
     */
    @Test
    void independentWorkspacesCannotCrossContaminateAdmiralState() throws Exception {
        Ship firstShip = new ShipImpl(
                ShipFaction.Federation,
                Tier.Tier6,
                Rarity.Epic,
                Role.Tac,
                "First Isolated Ship",
                30,
                20,
                10,
                RuleType.All.rewardBonus(0),
                "First Isolated Trait");
        Ship secondShip = new ShipImpl(
                ShipFaction.Federation,
                Tier.Tier6,
                Rarity.Epic,
                Role.Sci,
                "Second Isolated Ship",
                10,
                20,
                30,
                RuleType.All.rewardBonus(0),
                "Second Isolated Trait");
        GameData gameData = GameData.builder().ships(List.of(firstShip, secondShip)).build();
        Admiral first = new Admiral(gameData);
        Admiral second = new Admiral(gameData);
        first.addReusableShips(List.of(firstShip), RosterState.ACTIVE);
        second.addReusableShips(List.of(secondShip), RosterState.ACTIVE);
        first.getAssignment(0).setRequiredEng(30);
        first.getAssignment(0).setRequiredTac(20);
        first.getAssignment(0).setRequiredSci(10);
        second.getAssignment(0).setRequiredEng(10);
        second.getAssignment(0).setRequiredTac(20);
        second.getAssignment(0).setRequiredSci(30);
        AdmiralsStore admiralsStore = new AdmiralsStore();
        AtomicReference<AdmiralPanel> firstRootReference = new AtomicReference<AdmiralPanel>();
        AtomicReference<AdmiralPanel> secondRootReference = new AtomicReference<AdmiralPanel>();

        SwingUtilities.invokeAndWait(() -> {
            firstRootReference.set(new AdmiralPanel(
                    first,
                    gameData,
                    admiralsStore,
                    tempDir,
                    testIconRenderer()));
            secondRootReference.set(new AdmiralPanel(
                    second,
                    gameData,
                    admiralsStore,
                    tempDir,
                    testIconRenderer()));
        });
        AdmiralPanel firstRoot = firstRootReference.get();
        AdmiralPanel secondRoot = secondRootReference.get();
        ShipRosterPanel firstRoster = child(firstRoot, ShipRosterPanel.class);
        ShipRosterPanel secondRoster = child(secondRoot, ShipRosterPanel.class);
        AssignmentSelectionPanel firstAssignments = child(firstRoot, AssignmentSelectionPanel.class);
        AssignmentSelectionPanel secondAssignments = child(secondRoot, AssignmentSelectionPanel.class);
        StarshipTraitsPanel firstTraits = child(firstRoot, StarshipTraitsPanel.class);
        StarshipTraitsPanel secondTraits = child(secondRoot, StarshipTraitsPanel.class);
        RosterCard firstCard = first.getRoster().getActiveCards().getFirst();
        RosterCard secondCard = second.getRoster().getActiveCards().getFirst();

        SwingUtilities.invokeAndWait(() -> {
            formattedFieldAt(firstAssignments.pnlAssignments[0], 1, 3).setValue(99);
            buttonWithDescription(firstAssignments, DescPlanAssignments).doClick();
            buttonWithDescription(secondAssignments, DescPlanAssignments).doClick();

            assertAll(
                    () -> assertEquals(99, first.getAssignment(0).getRequiredEng()),
                    () -> assertEquals(10, second.getAssignment(0).getRequiredEng()),
                    () -> assertTrue(hasLabel(firstAssignments.pnlAssignments[0], firstShip.getDisplayName())),
                    () -> assertFalse(hasLabel(firstAssignments.pnlAssignments[0], secondShip.getDisplayName())),
                    () -> assertTrue(hasLabel(secondAssignments.pnlAssignments[0], secondShip.getDisplayName())),
                    () -> assertFalse(hasLabel(secondAssignments.pnlAssignments[0], firstShip.getDisplayName())));

            first.removeReusableCards(List.of(firstCard));

            assertAll(
                    () -> assertEquals(0, firstRoster.lstActive.getModel().getSize()),
                    () -> assertEquals(1, secondRoster.lstActive.getModel().getSize()),
                    () -> assertSame(secondCard, secondRoster.lstActive.getModel().getElementAt(0)),
                    () -> assertEquals(0, firstTraits.uiList.getModel().getSize()),
                    () -> assertEquals(1, secondTraits.uiList.getModel().getSize()),
                    () -> assertSame(secondCard, secondTraits.uiList.getModel().getElementAt(0)),
                    () -> assertTrue(hasLabel(firstAssignments.pnlAssignments[0], firstShip.getDisplayName())),
                    () -> assertTrue(hasLabel(secondAssignments.pnlAssignments[0], secondShip.getDisplayName())),
                    () -> assertFalse(hasLabel(secondAssignments.pnlAssignments[0], firstShip.getDisplayName())));
        });
    }

    /**
     * Verifies the root rejects construction and disposal off the Swing event
     * thread while projection, interaction, and icon rendering remain event-thread
     * confined.
     *
     * @throws Exception if Swing event-thread dispatch fails
     */
    @Test
    void workspaceLifecycleAndProjectionAreConfinedToTheSwingEventThread() throws Exception {
        Ship ship = ship("Event Thread Ship");
        GameData gameData = GameData.builder().ships(List.of(ship)).build();
        Admiral admiral = new Admiral(gameData);
        admiral.addReusableShips(List.of(ship), RosterState.ACTIVE);
        AdmiralsStore admiralsStore = new AdmiralsStore();
        RecordingIconRenderer iconRenderer = new RecordingIconRenderer();

        assertThrows(
                IllegalStateException.class,
                () -> new AdmiralPanel(admiral, gameData, admiralsStore, tempDir, iconRenderer));

        AtomicReference<AdmiralPanel> rootReference = new AtomicReference<AdmiralPanel>();
        SwingUtilities.invokeAndWait(() -> rootReference.set(new AdmiralPanel(
                admiral,
                gameData,
                admiralsStore,
                tempDir,
                iconRenderer)));
        AdmiralPanel root = rootReference.get();
        ShipRosterPanel roster = child(root, ShipRosterPanel.class);
        AssignmentSelectionPanel assignments = child(root, AssignmentSelectionPanel.class);
        JFormattedTextField requiredEng = formattedFieldAt(assignments.pnlAssignments[0], 1, 3);

        assertThrows(IllegalStateException.class, root::dispose);

        SwingUtilities.invokeAndWait(() -> {
            admiral.setFaction(com.kor.admiralty.enums.PlayerFaction.Klingon);
            admiral.getAssignment(0).setRequiredEng(55);
            assertEquals(55, ((Number) requiredEng.getValue()).intValue());
            Component rendered = renderCard(roster.lstActive, roster.lstActive.getModel().getElementAt(0));
            assertSame(iconRenderer.icon, primaryShipIcon(rendered));
            root.dispose();
        });

        assertFalse(iconRenderer.eventThreadCalls.isEmpty());
        assertTrue(iconRenderer.eventThreadCalls.stream().allMatch(Boolean::booleanValue));
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
        AdmiralsStore admiralsStore = new AdmiralsStore();

        AtomicReference<AdmiralPanel> panelReference = new AtomicReference<AdmiralPanel>();
        SwingUtilities.invokeAndWait(
                () -> panelReference.set(
                        new AdmiralPanel(admiral, gameData, admiralsStore, tempDir, iconRenderer)));
        AdmiralPanel panel = panelReference.get();
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
     * one-to-three-Assignment control all operate from root-supplied GameData on
     * the
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
        AdmiralsStore admiralsStore = new AdmiralsStore();

        AtomicReference<AdmiralPanel> panelReference = new AtomicReference<AdmiralPanel>();
        SwingUtilities.invokeAndWait(
                () -> panelReference.set(
                        new AdmiralPanel(admiral, gameData, admiralsStore, tempDir, testIconRenderer())));
        AdmiralPanel panel = panelReference.get();
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
     * Verifies that planning without a Solution reports the established message
     * through the root-supplied presenter instead of opening an untestable native
     * dialog.
     *
     * @throws Exception if persistence initialization or event-thread dispatch fails
     */
    @Test
    void planningWithoutASolutionUsesTheRootMessageSeam() throws Exception {
        GameData gameData = GameData.builder().build();
        Admiral admiral = new Admiral(gameData);
        AdmiralsStore admiralsStore = new AdmiralsStore();
        RecordingAssignmentMessageDialog messageDialog = new RecordingAssignmentMessageDialog();
        AdmiralPanel root = createRootOnEventThread(
                admiral,
                gameData,
                admiralsStore,
                testIconRenderer(),
                ShipRosterPanel.RosterFileDialog.swing(),
                messageDialog,
                RosterSelectionDialog.swing());

        SwingUtilities.invokeAndWait(
                () -> buttonWithDescription(root, DescPlanAssignments).doClick());

        assertEquals(List.of(MsgNoSolution), messageDialog.messages);
    }

    /**
     * Verifies one-to-three-Assignment planning, every navigation shortcut,
     * previous-Solution navigation, invalidation, and the no-deployment message.
     *
     * @throws Exception if persistence initialization or event-thread dispatch fails
     */
    @Test
    void planningContractCoversCountsNavigationInvalidationMessagesAndShortcuts() throws Exception {
        Ship firstShip = ship("Planning Flow One");
        Ship secondShip = ship("Planning Flow Two");
        Ship thirdShip = ship("Planning Flow Three");
        GameData gameData = GameData.builder()
                .ships(List.of(firstShip, secondShip, thirdShip))
                .build();
        Admiral admiral = new Admiral(gameData);
        admiral.addReusableShips(List.of(firstShip, secondShip, thirdShip), RosterState.ACTIVE);
        for (int index = 0; index < 3; index++) {
            admiral.getAssignment(index).setRequiredEng(10);
            admiral.getAssignment(index).setRequiredTac(20);
            admiral.getAssignment(index).setRequiredSci(30);
        }
        AdmiralsStore admiralsStore = new AdmiralsStore();
        RecordingAssignmentMessageDialog messageDialog = new RecordingAssignmentMessageDialog();
        AdmiralPanel root = createRootOnEventThread(
                admiral,
                gameData,
                admiralsStore,
                testIconRenderer(),
                ShipRosterPanel.RosterFileDialog.swing(),
                messageDialog,
                RosterSelectionDialog.swing());
        AssignmentSelectionPanel assignments = child(root, AssignmentSelectionPanel.class);

        SwingUtilities.invokeAndWait(() -> {
            List<AbstractButton> buttons = components(assignments, AbstractButton.class);
            for (int count = 1; count <= 3; count++) {
                int expectedCount = count;
                AbstractButton countButton = buttons.stream()
                        .filter(button -> button.getAction() != null)
                        .filter(button -> DescNumAssignments.equals(
                                button.getAction().getValue(Action.SHORT_DESCRIPTION)))
                        .filter(button -> Integer.toString(expectedCount).equals(
                                button.getAction().getValue(Action.NAME)))
                        .findFirst()
                        .orElseThrow();
                assertEquals(KeyEvent.VK_0 + count, countButton.getAction().getValue(Action.MNEMONIC_KEY));
                countButton.doClick();
                buttonWithDescription(assignments, DescPlanAssignments).doClick();
                assertFalse(assignments.solutions.isEmpty());
                assertEquals(count, assignments.solutions.getFirst().size());
                if (count == 1) {
                    assertTrue(assignments.solutions.size() > 1);
                    buttonWithDescription(assignments, DescNext).doClick();
                    assertEquals(1, assignments.solutionIndex);
                    buttonWithDescription(assignments, DescPrev).doClick();
                    assertEquals(0, assignments.solutionIndex);
                }
            }

            assertEquals(
                    KeyEvent.VK_P,
                    buttonWithDescription(assignments, DescPlanAssignments)
                            .getAction().getValue(Action.MNEMONIC_KEY));
            assertEquals(
                    KeyEvent.VK_C,
                    buttonWithDescription(assignments, DescClearAssignments)
                            .getAction().getValue(Action.MNEMONIC_KEY));
            assertEquals(
                    KeyEvent.VK_COMMA,
                    buttonWithDescription(assignments, DescPrev)
                            .getAction().getValue(Action.MNEMONIC_KEY));
            assertEquals(
                    KeyEvent.VK_B,
                    buttonWithDescription(assignments, DescBest)
                            .getAction().getValue(Action.MNEMONIC_KEY));
            assertEquals(
                    KeyEvent.VK_PERIOD,
                    buttonWithDescription(assignments, DescNext)
                            .getAction().getValue(Action.MNEMONIC_KEY));
            assertEquals(
                    KeyEvent.VK_D,
                    buttonWithDescription(assignments, DescDeployShips)
                            .getAction().getValue(Action.MNEMONIC_KEY));

            formattedFieldAt(assignments.pnlAssignments[0], 1, 3).setValue(11);
            assertTrue(assignments.solutions.isEmpty());
            assertEquals(-1, assignments.solutionIndex);
            buttonWithDescription(assignments, DescDeployShips).doClick();
        });

        assertEquals(List.of(MsgNoShipsToDeploy), messageDialog.messages);
    }

    /**
     * Verifies the root supplies one concrete persistence module and resolved data
     * directory to the Roster controls, whose file operations preserve canonical
     * names and the established success, no-op, and failure meanings.
     *
     * @throws Exception if file setup, persistence initialization, or Swing
     *                   event-thread dispatch fails
     */
    @Test
    void rosterImportExportUsesSuppliedDependenciesAndPreservesOutcomes() throws Exception {
        Ship exportedActive = ship("Exported Active Ship");
        Ship exportedMaintenance = ship("Exported Maintenance Ship");
        Ship canonicalImport = ship("Canonical Imported Ship");
        Ship oneTimeOnly = ship("One-Time Only Ship");
        GameData gameData = GameData.builder()
                .ships(List.of(exportedActive, exportedMaintenance, canonicalImport, oneTimeOnly))
                .renamedShips(Map.of("Former Imported Ship", canonicalImport.getName()))
                .build();
        Admiral admiral = new Admiral(gameData);
        admiral.setName("File Test Admiral");
        admiral.addReusableShips(List.of(exportedActive), RosterState.ACTIVE);
        admiral.addReusableShips(List.of(exportedMaintenance), RosterState.MAINTENANCE);
        admiral.adjustOneTimeShipQuantity(oneTimeOnly, 1);
        AdmiralsStore admiralsStore = new AdmiralsStore();
        Path defaultRosterFile = tempDir.resolve(admiral.getName() + ".txt");
        Path exportedFile = tempDir.resolve("exported.txt");
        Path noImportFile = tempDir.resolve("unknown.txt");
        Path missingFile = tempDir.resolve("missing.txt");
        Path unwritableExport = tempDir.resolve("missing-directory").resolve("exported.txt");
        Files.write(defaultRosterFile, List.of(
                "cAnOnIcAl ImPoRtEd ShIp",
                "Former Imported Ship",
                "Unknown Ship"));
        Files.write(noImportFile, List.of("Unknown Ship"));
        RecordingRosterFileDialog fileDialog = new RecordingRosterFileDialog(List.of(
                exportedFile.toFile(),
                unwritableExport.toFile(),
                defaultRosterFile.toFile(),
                noImportFile.toFile(),
                missingFile.toFile()));

        AtomicReference<AdmiralPanel> panelReference = new AtomicReference<AdmiralPanel>();
        SwingUtilities.invokeAndWait(() -> panelReference.set(
                new AdmiralPanel(
                        admiral,
                        gameData,
                        admiralsStore,
                        tempDir,
                        testIconRenderer(),
                        fileDialog)));
        AdmiralPanel panel = panelReference.get();

        SwingUtilities.invokeAndWait(() -> {
            AbstractButton exportButton = buttonWithDescription(panel, DescExportShips);
            AbstractButton importButton = buttonWithDescription(panel, DescImportShips);

            assertAll(
                    () -> assertEquals(LabelExportShips, exportButton.getText()),
                    () -> assertSame(Images.ICON_EXPORT, exportButton.getIcon()),
                    () -> assertEquals(0, exportButton.getMnemonic()),
                    () -> assertNull(exportButton.getAction().getValue(Action.MNEMONIC_KEY)),
                    () -> assertEquals(LabelImportShips, importButton.getText()),
                    () -> assertSame(Images.ICON_IMPORT, importButton.getIcon()),
                    () -> assertEquals(0, importButton.getMnemonic()),
                    () -> assertNull(importButton.getAction().getValue(Action.MNEMONIC_KEY)));

            exportButton.doClick();
            exportButton.doClick();
            importButton.doClick();
            importButton.doClick();
            importButton.doClick();
        });

        assertAll(
                () -> assertEquals(5, fileDialog.choosers.size()),
                () -> assertEquals(
                        List.of(
                                JFileChooser.SAVE_DIALOG,
                                JFileChooser.SAVE_DIALOG,
                                JFileChooser.OPEN_DIALOG,
                                JFileChooser.OPEN_DIALOG,
                                JFileChooser.OPEN_DIALOG),
                        fileDialog.choosers.stream().map(JFileChooser::getDialogType).toList()),
                () -> assertTrue(fileDialog.choosers.stream()
                        .allMatch(chooser -> tempDir.toFile().equals(chooser.getCurrentDirectory()))),
                () -> assertTrue(fileDialog.choosers.stream()
                        .allMatch(chooser -> defaultRosterFile.toFile().equals(chooser.getSelectedFile()))),
                () -> assertTrue(fileDialog.choosers.stream()
                        .allMatch(chooser -> chooser.getFileFilter() == TextFileFilter.SINGLETON)),
                () -> assertEquals(
                        List.of(
                                LabelExportShips,
                                LabelExportShips,
                                LabelImportShips,
                                LabelImportShips,
                                LabelImportShips),
                        fileDialog.approveLabels),
                () -> assertEquals(
                        List.of(
                                JOptionPane.INFORMATION_MESSAGE,
                                JOptionPane.ERROR_MESSAGE,
                                JOptionPane.INFORMATION_MESSAGE,
                                JOptionPane.INFORMATION_MESSAGE,
                                JOptionPane.ERROR_MESSAGE),
                        fileDialog.outcomes.stream()
                                .map(ShipRosterPanel.RosterFileOutcome::messageType)
                                .toList()),
                () -> assertEquals(
                        List.of(exportedActive.getDisplayName(), exportedMaintenance.getDisplayName()),
                        Files.readAllLines(exportedFile)),
                () -> assertSame(
                        canonicalImport,
                        admiral.getRoster().getActiveCards().stream()
                                .filter(card -> card.getShip().getName().equals(canonicalImport.getName()))
                                .findFirst()
                                .orElseThrow()
                                .getShip()),
                () -> assertEquals(RosterState.ACTIVE, admiral.getRoster().getReusableState(canonicalImport)),
                () -> assertFalse(Files.exists(tempDir.resolve("admirals.xml"))),
                () -> assertEquals(
                        new ShipRosterPanel.RosterFileOutcome(
                                ShipRosterPanel.RosterFileOutcome.Type.SUCCESS,
                                String.format(MsgExportSuccessful, exportedFile.getFileName()),
                                TitleExportShips),
                        fileDialog.outcomes.getFirst()),
                () -> assertEquals(
                        new ShipRosterPanel.RosterFileOutcome(
                                ShipRosterPanel.RosterFileOutcome.Type.FAILURE,
                                String.format(MsgExportFailed, unwritableExport.getFileName()),
                                TitleExportShips),
                        fileDialog.outcomes.get(1)),
                () -> assertEquals(
                        new ShipRosterPanel.RosterFileOutcome(
                                ShipRosterPanel.RosterFileOutcome.Type.SUCCESS,
                                String.format(MsgImportSuccessful, 2, defaultRosterFile.getFileName()),
                                TitleImportShips),
                        fileDialog.outcomes.get(2)),
                () -> assertEquals(
                        new ShipRosterPanel.RosterFileOutcome(
                                ShipRosterPanel.RosterFileOutcome.Type.NO_OP,
                                String.format(MsgNoImport, noImportFile.getFileName()),
                                TitleImportShips),
                        fileDialog.outcomes.get(3)),
                () -> assertEquals(
                        new ShipRosterPanel.RosterFileOutcome(
                                ShipRosterPanel.RosterFileOutcome.Type.FAILURE,
                                String.format(MsgImportFailed, missingFile.getFileName()),
                                TitleImportShips),
                        fileDialog.outcomes.get(4)));
    }

    /**
     * Verifies all three Roster lists retain their established wheel, unit, and
     * tracked block scrolling behavior through the production root.
     *
     * @throws Exception if persistence initialization or Swing event-thread
     *                   dispatch fails
     */
    @Test
    void rosterListsPreserveEstablishedScrollingIncrements() throws Exception {
        Ship activeShip = ship("Scrolling Active Ship");
        Ship maintenanceShip = ship("Scrolling Maintenance Ship");
        Ship oneTimeShip = ship("Scrolling One-Time Ship");
        GameData gameData = GameData.builder()
                .ships(List.of(activeShip, maintenanceShip, oneTimeShip))
                .build();
        Admiral admiral = new Admiral(gameData);
        admiral.addReusableShips(List.of(activeShip), RosterState.ACTIVE);
        admiral.addReusableShips(List.of(maintenanceShip), RosterState.MAINTENANCE);
        admiral.adjustOneTimeShipQuantity(oneTimeShip, 1);
        AdmiralsStore admiralsStore = new AdmiralsStore();

        AtomicReference<AdmiralPanel> panelReference = new AtomicReference<AdmiralPanel>();
        SwingUtilities.invokeAndWait(() -> panelReference.set(
                new AdmiralPanel(admiral, gameData, admiralsStore, tempDir, testIconRenderer())));
        AdmiralPanel panel = panelReference.get();
        ShipRosterPanel rosterPanel = child(panel, ShipRosterPanel.class);
        OneTimeShipPanel oneTimePanel = child(panel, OneTimeShipPanel.class);

        SwingUtilities.invokeAndWait(() -> {
            JScrollPane activePane = (JScrollPane) SwingUtilities.getAncestorOfClass(
                    JScrollPane.class,
                    rosterPanel.lstActive);
            JScrollPane maintenancePane = (JScrollPane) SwingUtilities.getAncestorOfClass(
                    JScrollPane.class,
                    rosterPanel.lstMaintenance);
            JScrollPane oneTimePane = (JScrollPane) SwingUtilities.getAncestorOfClass(
                    JScrollPane.class,
                    oneTimePanel.uiList);
            JScrollBar activeBar = activePane.getVerticalScrollBar();
            JScrollBar maintenanceBar = maintenancePane.getVerticalScrollBar();
            JScrollBar oneTimeBar = oneTimePane.getVerticalScrollBar();

            assertAll(
                    () -> assertTrue(activePane.isWheelScrollingEnabled()),
                    () -> assertTrue(maintenancePane.isWheelScrollingEnabled()),
                    () -> assertTrue(oneTimePane.isWheelScrollingEnabled()),
                    () -> assertEquals(76, activeBar.getUnitIncrement()),
                    () -> assertEquals(76, activeBar.getUnitIncrement(1)),
                    () -> assertEquals(76, maintenanceBar.getUnitIncrement()),
                    () -> assertEquals(76, maintenanceBar.getUnitIncrement(1)),
                    // One-Time wheel movement remains row-derived rather than forcing 76.
                    () -> assertEquals(1, oneTimeBar.getUnitIncrement()));

            for (JScrollBar scrollBar : List.of(activeBar, maintenanceBar, oneTimeBar)) {
                scrollBar.setBlockIncrement(13);
                AdjustmentEvent unitEvent = new AdjustmentEvent(
                        scrollBar,
                        AdjustmentEvent.ADJUSTMENT_VALUE_CHANGED,
                        AdjustmentEvent.UNIT_INCREMENT,
                        scrollBar.getValue());
                for (AdjustmentListener listener : scrollBar.getAdjustmentListeners()) {
                    listener.adjustmentValueChanged(unitEvent);
                }
                assertEquals(13, scrollBar.getBlockIncrement());

                AdjustmentEvent trackEvent = new AdjustmentEvent(
                        scrollBar,
                        AdjustmentEvent.ADJUSTMENT_VALUE_CHANGED,
                        AdjustmentEvent.TRACK,
                        scrollBar.getValue());
                for (AdjustmentListener listener : scrollBar.getAdjustmentListeners()) {
                    listener.adjustmentValueChanged(trackEvent);
                }
                assertEquals(76, scrollBar.getBlockIncrement());
                assertEquals(76, scrollBar.getBlockIncrement(1));
            }
        });
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
        AdmiralsStore admiralsStore = new AdmiralsStore();

        RecordingAssignmentMessageDialog messageDialog = new RecordingAssignmentMessageDialog();
        AdmiralPanel root = createRootOnEventThread(
                admiral,
                gameData,
                admiralsStore,
                iconRenderer,
                ShipRosterPanel.RosterFileDialog.swing(),
                messageDialog,
                RosterSelectionDialog.swing());
        AssignmentSelectionPanel panel = child(root, AssignmentSelectionPanel.class);

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
        assertTrue(messageDialog.messages.getFirst().toString().contains("One-time ship(s) assigned"));

        RosterView afterDeployment = admiral.getRoster();
        SwingUtilities.invokeAndWait(() -> buttonWithDescription(panel, DescDeployShips).doClick());
        assertSame(afterDeployment, admiral.getRoster());
        assertEquals(1, admiral.getUsageCounts().get(sharedShip.getName()));
        assertTrue(messageDialog.messages.get(1).toString().contains("Please plan again"));
    }

    /**
     * Verifies passive Active, Maintenance, One-Time, and Starship Trait lists
     * retain canonical ordering, exact card identities, and the established trait
     * scrolling layout.
     *
     * @throws Exception if Swing event-thread dispatch fails
     */
    @Test
    void passiveShipPresentationsRetainOrderingIdentityAndTraitScrolling() throws Exception {
        Ship alpha = new ShipImpl(
                ShipFaction.Federation,
                Tier.Tier1,
                Rarity.Common,
                Role.Eng,
                "Alpha",
                10,
                20,
                30,
                RuleType.All.rewardBonus(0),
                "Alpha Trait");
        Ship beta = new ShipImpl(
                ShipFaction.Federation,
                Tier.Tier2,
                Rarity.Uncommon,
                Role.Sci,
                "Beta",
                10,
                20,
                30,
                RuleType.All.rewardBonus(0),
                "Beta Trait");
        Ship gamma = new ShipImpl(
                ShipFaction.Federation,
                Tier.Tier3,
                Rarity.Rare,
                Role.Tac,
                "Gamma",
                10,
                20,
                30,
                RuleType.All.rewardBonus(0),
                "Gamma Trait");
        Ship delta = new ShipImpl(
                ShipFaction.Federation,
                Tier.Tier4,
                Rarity.VeryRare,
                Role.Eng,
                "Delta",
                10,
                20,
                30,
                RuleType.All.rewardBonus(0),
                "Delta Trait");
        GameData gameData = GameData.builder().ships(List.of(delta, beta, gamma, alpha)).build();
        Admiral admiral = new Admiral(gameData);
        admiral.addReusableShips(List.of(beta, alpha), RosterState.ACTIVE);
        admiral.addReusableShips(List.of(delta, gamma), RosterState.MAINTENANCE);
        admiral.adjustOneTimeShipQuantity(beta, 1);
        admiral.adjustOneTimeShipQuantity(alpha, 1);
        AdmiralPanel root = createRootOnEventThread(
                admiral,
                gameData,
                new AdmiralsStore(),
                testIconRenderer(),
                ShipRosterPanel.RosterFileDialog.swing(),
                AssignmentSelectionPanel.MessageDialog.swing(),
                RosterSelectionDialog.swing());
        ShipRosterPanel roster = child(root, ShipRosterPanel.class);
        OneTimeShipPanel oneTime = child(root, OneTimeShipPanel.class);
        StarshipTraitsPanel traits = child(root, StarshipTraitsPanel.class);

        assertAll(
                () -> assertSame(alpha, roster.lstActive.getModel().getElementAt(0).getShip()),
                () -> assertSame(beta, roster.lstActive.getModel().getElementAt(1).getShip()),
                () -> assertSame(gamma, roster.lstMaintenance.getModel().getElementAt(0).getShip()),
                () -> assertSame(delta, roster.lstMaintenance.getModel().getElementAt(1).getShip()),
                () -> assertSame(alpha, oneTime.uiList.getModel().getElementAt(0).getShip()),
                () -> assertSame(beta, oneTime.uiList.getModel().getElementAt(1).getShip()),
                () -> assertSame(alpha, traits.uiList.getModel().getElementAt(0).getShip()),
                () -> assertSame(beta, traits.uiList.getModel().getElementAt(1).getShip()),
                () -> assertSame(gamma, traits.uiList.getModel().getElementAt(2).getShip()),
                () -> assertSame(delta, traits.uiList.getModel().getElementAt(3).getShip()));

        JScrollPane traitPane = (JScrollPane) SwingUtilities.getAncestorOfClass(
                JScrollPane.class,
                traits.uiList);
        assertAll(
                () -> assertTrue(traitPane.isWheelScrollingEnabled()),
                () -> assertEquals(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
                        traitPane.getHorizontalScrollBarPolicy()),
                () -> assertEquals(JList.VERTICAL, traits.uiList.getLayoutOrientation()),
                () -> assertNotNull(traits.uiList.getCellRenderer()));
    }

    /**
     * Verifies double-click activation on each reusable Roster list moves the
     * exact visible card rather than whichever card previously occupied its index.
     *
     * @throws Exception if Swing event-thread dispatch fails
     */
    @Test
    void rosterDoubleClickActivationUsesTheExactVisibleCard() throws Exception {
        Ship alpha = ship("Alpha Double Click");
        Ship beta = ship("Beta Double Click");
        GameData gameData = GameData.builder().ships(List.of(beta, alpha)).build();
        Admiral admiral = new Admiral(gameData);
        admiral.addReusableShips(List.of(beta, alpha), RosterState.ACTIVE);
        AdmiralPanel root = createRootOnEventThread(
                admiral,
                gameData,
                new AdmiralsStore(),
                testIconRenderer(),
                ShipRosterPanel.RosterFileDialog.swing(),
                AssignmentSelectionPanel.MessageDialog.swing(),
                RosterSelectionDialog.swing());
        ShipRosterPanel roster = child(root, ShipRosterPanel.class);

        SwingUtilities.invokeAndWait(() -> doubleClick(roster.lstActive, 1));
        assertEquals(RosterState.ACTIVE, admiral.getRoster().getReusableState(alpha));
        assertEquals(RosterState.MAINTENANCE, admiral.getRoster().getReusableState(beta));

        SwingUtilities.invokeAndWait(() -> doubleClick(roster.lstMaintenance, 0));
        assertEquals(RosterState.ACTIVE, admiral.getRoster().getReusableState(alpha));
        assertEquals(RosterState.ACTIVE, admiral.getRoster().getReusableState(beta));
    }

    /**
     * Records Assignment and deployment dialogs at the root's Swing boundary.
     */
    private static final class RecordingAssignmentMessageDialog
            implements AssignmentSelectionPanel.MessageDialog {

        private final List<Object> messages = new ArrayList<Object>();

        /**
         * Records dialog content without opening a native window.
         */
        @Override
        public void show(Window owner, Object message) {
            messages.add(message);
        }
    }

    /** Supplies deterministic selections to every Roster dialog action. */
    private static final class RecordingRosterSelectionDialog implements RosterSelectionDialog {

        private final Ship reusableShip;
        private final Ship oneTimeShip;
        private int reusableAdditions;
        private int reusableRemovals;
        private int oneTimeAdditions;
        private int oneTimeRemovals;

        /** Creates one adapter around the Ships selected by the scenario. */
        private RecordingRosterSelectionDialog(Ship reusableShip, Ship oneTimeShip) {
            this.reusableShip = reusableShip;
            this.oneTimeShip = oneTimeShip;
        }

        /** Returns the configured reusable Ship from the production candidate set. */
        @Override
        public List<Ship> chooseReusableShips(
                Window owner,
                com.kor.admiralty.enums.PlayerFaction faction,
                Collection<Ship> candidates,
                ShipIconFactory iconRenderer) {
            reusableAdditions++;
            assertTrue(candidates.contains(reusableShip));
            return List.of(reusableShip);
        }

        /** Returns two copies so one action proves quantity accumulation. */
        @Override
        public List<Ship> chooseOneTimeShips(
                Window owner,
                com.kor.admiralty.enums.PlayerFaction faction,
                Collection<Ship> candidates,
                ShipIconFactory iconRenderer) {
            oneTimeAdditions++;
            assertTrue(candidates.contains(oneTimeShip));
            return List.of(oneTimeShip, oneTimeShip);
        }

        /** Selects the configured reusable Ship once, then the One-Time type. */
        @Override
        public List<RosterCard> chooseRosterCards(
                Window owner,
                List<RosterCard> candidates,
                ShipIconFactory iconRenderer,
                String title) {
            Ship selectedShip;
            if (reusableRemovals == 0) {
                reusableRemovals++;
                selectedShip = reusableShip;
            } else {
                oneTimeRemovals++;
                selectedShip = oneTimeShip;
            }
            return List.of(candidates.stream()
                    .filter(card -> card.getShip() == selectedShip)
                    .findFirst()
                    .orElseThrow());
        }
    }

    /**
     * Captures one Ship icon-rendering request at the explicit workspace boundary.
     */
    private record IconRequest(
            String iconName,
            ShipFaction faction,
            Role role,
            Rarity rarity,
            boolean owned) {
    }

    /**
     * Records icon facts while returning deterministic in-memory artwork.
     */
    private static final class RecordingIconRenderer implements ShipIconFactory {

        private final ImageIcon icon = new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        private final List<IconRequest> requests = new ArrayList<IconRequest>();
        private final List<Boolean> eventThreadCalls = new ArrayList<Boolean>();

        /**
         * Records exact presentation facts and returns the isolated test icon.
         */
        @Override
        public ImageIcon getIcon(String iconName, ShipFaction faction, Role role, Rarity rarity, boolean owned) {
            requests.add(new IconRequest(iconName, faction, role, rarity, owned));
            eventThreadCalls.add(SwingUtilities.isEventDispatchThread());
            return icon;
        }
    }

    /**
     * Records the real chooser configuration and presents deterministic selections.
     */
    private static final class RecordingRosterFileDialog implements ShipRosterPanel.RosterFileDialog {

        private final List<File> selections;
        private final List<JFileChooser> choosers = new ArrayList<JFileChooser>();
        private final List<String> approveLabels = new ArrayList<String>();
        private final List<ShipRosterPanel.RosterFileOutcome> outcomes = new ArrayList<ShipRosterPanel.RosterFileOutcome>();
        private int selectionIndex;

        /**
         * Creates a headless dialog boundary that returns files in click order.
         *
         * @param selections selected files to return for successive chooser requests
         */
        private RecordingRosterFileDialog(List<File> selections) {
            this.selections = List.copyOf(selections);
        }

        /**
         * Records chooser state before returning the next deterministic selection.
         */
        @Override
        public File chooseFile(Window owner, JFileChooser chooser, String approveLabel) {
            choosers.add(chooser);
            approveLabels.add(approveLabel);
            return selections.get(selectionIndex++);
        }

        /**
         * Records one success, no-op, or failure presentation without opening a window.
         */
        @Override
        public void showOutcome(Window owner, ShipRosterPanel.RosterFileOutcome outcome) {
            outcomes.add(outcome);
        }
    }

    /**
     * Records listener ownership while retaining the real Admiral behavior.
     */
    private static final class TrackingAdmiral extends Admiral {

        private int propertyListenerAdds;
        private int propertyListenerRemoves;
        private int rosterListenerAdds;
        private int rosterListenerRemoves;
        private int assignmentLookups;

        /**
         * Creates a listener-observable Admiral over the supplied reference data.
         */
        private TrackingAdmiral(GameData gameData) {
            super(gameData);
        }

        /**
         * Records one property-listener acquisition before delegating to Admiral.
         */
        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {
            propertyListenerAdds++;
            super.addPropertyChangeListener(listener);
        }

        /**
         * Records one property-listener release before delegating to Admiral.
         */
        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {
            propertyListenerRemoves++;
            super.removePropertyChangeListener(listener);
        }

        /**
         * Records one Roster-listener acquisition before delegating to Admiral.
         */
        @Override
        public void addRosterChangeListener(RosterChangeListener listener) {
            rosterListenerAdds++;
            super.addRosterChangeListener(listener);
        }

        /**
         * Records one Roster-listener release before delegating to Admiral.
         */
        @Override
        public void removeRosterChangeListener(RosterChangeListener listener) {
            rosterListenerRemoves++;
            super.removeRosterChangeListener(listener);
        }

        /**
         * Records one root lookup of an Assignment receiving reported user intent.
         */
        @Override
        public Assignment getAssignment(int index) {
            assignmentLookups++;
            return super.getAssignment(index);
        }

        /**
         * Returns an Assignment for assertions without affecting intent lookup counts.
         */
        private Assignment assignmentAt(int index) {
            return super.getAssignment(index);
        }
    }
}
