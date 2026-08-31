/*******************************************************************************
 * Copyright (C) 2015, 2019 Dave Kor
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/
package com.kor.admiralty.ui.panels;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;

import javax.swing.JTabbedPane;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;

import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.CompositeSolution;
import com.kor.admiralty.beans.DeploymentOutcome;
import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.RosterChange;
import com.kor.admiralty.beans.RosterChangeListener;
import com.kor.admiralty.beans.RosterState;
import com.kor.admiralty.beans.RosterView;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.enums.PlayerFaction;
import com.kor.admiralty.enums.ShipPriority;
import com.kor.admiralty.io.AdmiralsStore;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.ui.resources.ShipIconFactory;

import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.*;

import javax.swing.JLabel;
import javax.swing.JTextField;
import java.awt.Insets;
import java.beans.Beans;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.Serial;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import javax.swing.JComboBox;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public class AdmiralPanel2 extends JPanel implements PropertyChangeListener, RosterChangeListener {

    @Serial
    private static final long serialVersionUID = 6385797348923426101L;

    private final Admiral admiral;
    private final ShipRosterPanel pnlPrimaryShips;
    private final OneTimeShipPanel pnlOneTime;
    private final AssignmentSelectionPanel pnlAssignments;
    private final StarshipTraitsPanel pnlStarshipTraits;
    protected JTextField txtName;
    protected JComboBox<PlayerFaction> cbxFaction;
    protected JComboBox<ShipPriority> cbxShipPriority;
    private RosterView rosterView;
    private boolean disposed;
    private boolean projecting;

    /**
     * Creates the replacement Admiral workspace and supplies every child from the
     * same explicit GameData, persistence, data-directory, and Ship-presentation
     * seam.
     * Construction and subsequent interaction are expected on the Swing event
     * thread.
     *
     * @param admiral fixed initial Admiral selection
     * @param gameData read-only reference data used by Ship, Assignment, and Event lookup
     * @param admiralsStore concrete Admirals persistence used for Roster file transfer
     * @param dataDirectory resolved application data directory used by file choosers
     * @param iconRenderer renderer for reusable and One-Time Ship presentation
     * @throws NullPointerException if any dependency is {@code null}
     * @throws IllegalStateException if construction occurs outside the Swing event thread
     */
    public AdmiralPanel2(
            Admiral admiral,
            GameData gameData,
            AdmiralsStore admiralsStore,
            Path dataDirectory,
            ShipIconFactory iconRenderer) {
        this(
                admiral,
                gameData,
                admiralsStore,
                dataDirectory,
                iconRenderer,
                ShipRosterPanel.RosterFileDialog.swing());
    }

    /**
     * Builds the root with a narrow file-dialog boundary so headless integration
     * tests can click the real Roster actions without opening native windows.
     *
     * @param admiral fixed initial Admiral selection
     * @param gameData read-only reference data used by Ship, Assignment, and Event lookup
     * @param admiralsStore concrete Admirals persistence used for Roster file transfer
     * @param dataDirectory resolved application data directory used by file choosers
     * @param iconRenderer renderer for reusable and One-Time Ship presentation
     * @param rosterFileDialog file selection and outcome-presentation boundary
     * @throws NullPointerException if any dependency is {@code null}
     * @throws IllegalStateException if construction occurs outside the Swing event thread
     */
    AdmiralPanel2(
            Admiral admiral,
            GameData gameData,
            AdmiralsStore admiralsStore,
            Path dataDirectory,
            ShipIconFactory iconRenderer,
            ShipRosterPanel.RosterFileDialog rosterFileDialog) {
        this(
                admiral,
                gameData,
                admiralsStore,
                dataDirectory,
                iconRenderer,
                rosterFileDialog,
                AssignmentSelectionPanel.MessageDialog.swing());
    }

    /**
     * Builds the root with narrow file and message boundaries for headless root
     * integration tests.
     *
     * @param admiral fixed construction-time Admiral
     * @param gameData read-only reference data used by Ship, Assignment, and Event lookup
     * @param admiralsStore concrete Admirals persistence used for Roster file transfer
     * @param dataDirectory resolved application data directory used by file choosers
     * @param iconRenderer renderer for reusable and One-Time Ship presentation
     * @param rosterFileDialog file selection and outcome-presentation boundary
     * @param assignmentMessageDialog Assignment and deployment message boundary
     * @throws NullPointerException if any dependency is {@code null}
     * @throws IllegalStateException if construction occurs outside the Swing event thread
     */
    AdmiralPanel2(
            Admiral admiral,
            GameData gameData,
            AdmiralsStore admiralsStore,
            Path dataDirectory,
            ShipIconFactory iconRenderer,
            ShipRosterPanel.RosterFileDialog rosterFileDialog,
            AssignmentSelectionPanel.MessageDialog assignmentMessageDialog) {
        requireEventDispatchThread("construct an Admiral workspace");
        this.admiral = Objects.requireNonNull(admiral, "admiral");
        Objects.requireNonNull(gameData, "gameData");
        Objects.requireNonNull(admiralsStore, "admiralsStore");
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Objects.requireNonNull(iconRenderer, "iconRenderer");
        Objects.requireNonNull(rosterFileDialog, "rosterFileDialog");
        Objects.requireNonNull(assignmentMessageDialog, "assignmentMessageDialog");
        setLayout(new BorderLayout(5, 5));

        JPanel pnlAdmiral = new JPanel();
        pnlAdmiral.setBorder(null);
        add(pnlAdmiral, BorderLayout.NORTH);
        GridBagLayout gbl_pnlAdmiral = new GridBagLayout();
        gbl_pnlAdmiral.columnWidths = new int[] { 0, 0, 0, 0, 0 };
        gbl_pnlAdmiral.rowHeights = new int[] { 0 };
        gbl_pnlAdmiral.columnWeights = new double[] { 0.0, 1.0, 0.0, 0.0, 0.0 };
        gbl_pnlAdmiral.rowWeights = new double[] { 0.0 };
        pnlAdmiral.setLayout(gbl_pnlAdmiral);

        JLabel lblName = new JLabel(LabelName);
        GridBagConstraints gbc_lblName = new GridBagConstraints();
        gbc_lblName.weightx = 1.0;
        gbc_lblName.fill = GridBagConstraints.HORIZONTAL;
        gbc_lblName.insets = new Insets(5, 5, 5, 5);
        gbc_lblName.gridx = 0;
        gbc_lblName.gridy = 0;
        pnlAdmiral.add(lblName, gbc_lblName);

        txtName = new JTextField();
        txtName.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateAdmiralName();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateAdmiralName();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateAdmiralName();
            }
        });
        GridBagConstraints gbc_txtName = new GridBagConstraints();
        gbc_txtName.fill = GridBagConstraints.HORIZONTAL;
        gbc_txtName.weightx = 100.0;
        gbc_txtName.anchor = GridBagConstraints.WEST;
        gbc_txtName.insets = new Insets(5, 0, 5, 5);
        gbc_txtName.gridx = 1;
        gbc_txtName.gridy = 0;
        pnlAdmiral.add(txtName, gbc_txtName);
        txtName.setColumns(20);

        JLabel lblFaction = new JLabel(LabelFaction);
        lblFaction.setHorizontalAlignment(SwingConstants.RIGHT);
        GridBagConstraints gbc_lblFaction = new GridBagConstraints();
        gbc_lblFaction.weightx = 1.0;
        gbc_lblFaction.fill = GridBagConstraints.HORIZONTAL;
        gbc_lblFaction.insets = new Insets(5, 5, 5, 5);
        gbc_lblFaction.gridx = 2;
        gbc_lblFaction.gridy = 0;
        pnlAdmiral.add(lblFaction, gbc_lblFaction);

        if (Beans.isDesignTime()) {
            cbxFaction = new JComboBox<PlayerFaction>();
        } else {
            cbxFaction = new JComboBox<PlayerFaction>(PlayerFaction.values());
            cbxFaction.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    PlayerFaction faction = (PlayerFaction) cbxFaction.getSelectedItem();
                    if (acceptsUserIntent()) {
                        admiral.setFaction(faction);
                    }
                }
            });
        }
        GridBagConstraints gbc_cbxFaction = new GridBagConstraints();
        gbc_cbxFaction.weightx = 10.0;
        gbc_cbxFaction.anchor = GridBagConstraints.WEST;
        gbc_cbxFaction.insets = new Insets(5, 5, 5, 5);
        gbc_cbxFaction.gridx = 3;
        gbc_cbxFaction.gridy = 0;
        pnlAdmiral.add(cbxFaction, gbc_cbxFaction);

        JLabel lblShipPriority = new JLabel(LabelShipPriority);
        lblShipPriority.setHorizontalAlignment(SwingConstants.RIGHT);
        GridBagConstraints gbc_lblShipPriority = new GridBagConstraints();
        gbc_lblShipPriority.fill = GridBagConstraints.HORIZONTAL;
        gbc_lblShipPriority.weightx = 1.0;
        gbc_lblFaction.weightx = 1.0;
        gbc_lblFaction.fill = GridBagConstraints.HORIZONTAL;
        gbc_lblFaction.insets = new Insets(5, 5, 5, 5);
        gbc_lblFaction.gridx = 4;
        gbc_lblFaction.gridy = 0;
        pnlAdmiral.add(lblShipPriority, gbc_lblShipPriority);

        if (Beans.isDesignTime()) {
            cbxShipPriority = new JComboBox<ShipPriority>();
        } else {
            cbxShipPriority = new JComboBox<ShipPriority>(ShipPriority.values());
            cbxShipPriority.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    ShipPriority priority = (ShipPriority) cbxShipPriority.getSelectedItem();
                    if (acceptsUserIntent()) {
                        admiral.setPrioritizeActive(priority == ShipPriority.Active);
                    }
                }
            });
        }
        GridBagConstraints gbc_btnShipPriority = new GridBagConstraints();
        gbc_btnShipPriority.anchor = GridBagConstraints.WEST;
        gbc_btnShipPriority.weightx = 10.0;
        gbc_cbxFaction.weightx = 10.0;
        gbc_cbxFaction.anchor = GridBagConstraints.WEST;
        gbc_cbxFaction.insets = new Insets(5, 5, 5, 5);
        gbc_cbxFaction.gridx = 5;
        gbc_cbxFaction.gridy = 0;
        pnlAdmiral.add(cbxShipPriority, gbc_btnShipPriority);

        JTabbedPane tabAdmiral = new JTabbedPane(JTabbedPane.TOP);
        add(tabAdmiral, BorderLayout.CENTER);

        pnlPrimaryShips = new ShipRosterPanel(
                gameData,
                dataDirectory,
                iconRenderer,
                rosterFileDialog,
                createRosterActions(gameData, admiralsStore));
        tabAdmiral.addTab(TabPrimary, null, pnlPrimaryShips, null);

        pnlOneTime = new OneTimeShipPanel(gameData, iconRenderer, createOneTimeActions());
        tabAdmiral.addTab(LabelOneTimeShips, null, pnlOneTime, null);

        pnlAssignments = new AssignmentSelectionPanel(
                gameData,
                iconRenderer,
                createAssignmentActions(),
                assignmentMessageDialog);
        tabAdmiral.addTab(TabAssignments, null, pnlAssignments, null);

        pnlStarshipTraits = new StarshipTraitsPanel(iconRenderer);
        tabAdmiral.addTab("Starship Traits", null, pnlStarshipTraits, null);
        initializeWorkspace();
    }

    /**
     * Initializes every child from one construction-time projection, then registers
     * the root's sole Admiral subscriptions exactly once.
     */
    private void initializeWorkspace() {
        rosterView = admiral.getRoster();
        renderIdentity();
        renderPlanningState();
        renderRoster(rosterView);
        admiral.addPropertyChangeListener(this);
        admiral.addRosterChangeListener(this);
    }

    /** Updates the fixed Admiral's name for one root-owned document interaction. */
    private void updateAdmiralName() {
        if (acceptsUserIntent()) {
            admiral.setName(txtName.getText());
        }
    }

    /**
     * Confirms one internal callback is running on the event thread and the
     * workspace still owns its lifetime.
     *
     * @return {@code true} while user intent may reach the fixed Admiral
     */
    private boolean acceptsUserIntent() {
        requireEventDispatchThread("handle Admiral workspace interaction");
        return !disposed && !projecting;
    }

    /** Renders name, faction, and priority from the fixed Admiral. */
    private void renderIdentity() {
        projecting = true;
        try {
            if (!admiral.getName().equals(txtName.getText())) {
                txtName.setText(admiral.getName());
            }
            cbxFaction.setSelectedItem(admiral.getFaction());
            cbxShipPriority.setSelectedItem(
                    admiral.getPrioritizeActive() ? ShipPriority.Active : ShipPriority.OneTime);
        } finally {
            projecting = false;
        }
        pnlPrimaryShips.render(admiral.getName(), admiral.getFaction(), rosterView);
        pnlOneTime.render(admiral.getFaction(), rosterView);
    }

    /** Renders current Assignment identities and visible count from one root read. */
    private void renderPlanningState() {
        pnlAssignments.renderAssignments(admiral.getAssignmentCount(), admiral.getAssignments());
    }

    /**
     * Fans one immutable Roster revision out to all four internal views without any
     * child re-reading Admiral state.
     *
     * @param roster complete immutable Roster view
     */
    private void renderRoster(RosterView roster) {
        rosterView = Objects.requireNonNull(roster, "roster");
        pnlPrimaryShips.render(admiral.getName(), admiral.getFaction(), roster);
        pnlOneTime.render(admiral.getFaction(), roster);
        pnlAssignments.renderRoster(roster);
        pnlStarshipTraits.renderRoster(roster);
    }

    /** Renders one synchronous Admiral property change on the Swing event thread. */
    @Override
    public void propertyChange(PropertyChangeEvent e) {
        requireEventDispatchThread("project an Admiral property change");
        if (disposed) {
            return;
        }
        String property = e.getPropertyName();
        if (Admiral.PROP_NAME.equals(property)
                || Admiral.PROP_FACTION.equals(property)
                || Admiral.PROP_PRIORITIZEACTIVE.equals(property)) {
            renderIdentity();
        } else if (Admiral.PROP_ASSIGNMENTCOUNT.equals(property)
                || Admiral.PROP_ASSIGNMENTS.equals(property)) {
            renderPlanningState();
        }
    }

    /** Projects one committed Roster revision to every child on the event thread. */
    @Override
    public void rosterChanged(RosterChange change) {
        requireEventDispatchThread("project a committed Roster change");
        if (!disposed) {
            renderRoster(change.getAfter());
        }
    }

    /**
     * Permanently releases the root's Admiral subscriptions and nested Assignment
     * subscriptions. Duplicate calls are safe and perform no additional removal.
     * This operation must run on the Swing event thread.
     */
    public void dispose() {
        requireEventDispatchThread("dispose an Admiral workspace");
        if (disposed) {
            return;
        }
        // Mark first so synchronous callbacks cannot re-enter the workspace while its
        // listener ownership is being dismantled.
        disposed = true;
        admiral.removePropertyChangeListener(this);
        admiral.removeRosterChangeListener(this);
        pnlAssignments.dispose();
        disableComponentTree(this);
    }

    /** Disables every retained Swing control after one-way workspace disposal. */
    private static void disableComponentTree(Component component) {
        component.setEnabled(false);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                disableComponentTree(child);
            }
        }
    }

    /**
     * Rejects lifecycle and projection work that would mutate Swing state outside
     * the event thread.
     *
     * @param operation human-readable operation for diagnostics
     * @throws IllegalStateException when called outside the Swing event thread
     */
    private static void requireEventDispatchThread(String operation) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Must " + operation + " on the Swing event thread");
        }
    }

    /** Creates the narrow reusable-Roster intent boundary owned by this root. */
    private ShipRosterPanel.Actions createRosterActions(GameData gameData, AdmiralsStore admiralsStore) {
        return new ShipRosterPanel.Actions() {

            @Override
            public void moveReusableCards(List<RosterCard> cards, RosterState destination) {
                if (acceptsUserIntent()) {
                    admiral.moveReusableCards(cards, destination);
                }
            }

            @Override
            public void addReusableShips(List<Ship> ships, RosterState destination) {
                if (acceptsUserIntent()) {
                    admiral.addReusableShips(ships, destination);
                }
            }

            @Override
            public void removeReusableCards(List<RosterCard> cards) {
                if (acceptsUserIntent()) {
                    admiral.removeReusableCards(cards);
                }
            }

            @Override
            public boolean exportShipNames(File file, Collection<Ship> ships) {
                return acceptsUserIntent() && admiralsStore.exportShipNames(file, ships);
            }

            @Override
            public int importShipNames(File file) {
                return acceptsUserIntent() ? admiralsStore.importShipNames(file, gameData, admiral) : -1;
            }
        };
    }

    /** Creates the narrow One-Time Ship intent boundary owned by this root. */
    private OneTimeShipPanel.Actions createOneTimeActions() {
        return (ships, adjustmentPerOccurrence) -> {
            if (acceptsUserIntent()) {
                admiral.adjustOneTimeShipQuantities(ships, adjustmentPerOccurrence);
            }
        };
    }

    /** Creates the narrow Assignment and Solution intent boundary owned by this root. */
    private AssignmentSelectionPanel.Actions createAssignmentActions() {
        return new AssignmentSelectionPanel.Actions() {

            @Override
            public void setAssignmentCount(int assignmentCount) {
                if (acceptsUserIntent()) {
                    admiral.setAssignmentCount(assignmentCount);
                }
            }

            @Override
            public List<CompositeSolution> solveAssignments() {
                return acceptsUserIntent() ? admiral.solveAssignments() : List.of();
            }

            @Override
            public int clearAssignments() {
                if (!acceptsUserIntent()) {
                    return 0;
                }
                int count = admiral.getAssignmentCount();
                for (int index = 0; index < count; index++) {
                    admiral.getAssignment(index).clear();
                }
                return count;
            }

            @Override
            public DeploymentOutcome deploySolution(CompositeSolution solution) {
                if (!acceptsUserIntent()) {
                    throw new IllegalStateException("Cannot deploy from a disposed Admiral workspace");
                }
                return admiral.deploySolution(solution);
            }
        };
    }
}
