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
import java.awt.GridBagLayout;
import javax.swing.JLabel;
import javax.swing.JList;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.Serial;
import java.util.List;
import java.util.TreeSet;

import javax.swing.JButton;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.RosterChange;
import com.kor.admiralty.beans.RosterChangeListener;
import com.kor.admiralty.beans.RosterState;
import com.kor.admiralty.beans.RosterView;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.App;
import com.kor.admiralty.ui.ShipSelectionPanel;
import com.kor.admiralty.ui.ShipListPanel;
import com.kor.admiralty.ui.models.RosterCardListModel;
import com.kor.admiralty.ui.renderers.RosterCardCellRenderer;
import com.kor.admiralty.ui.resources.Images;

import static com.kor.admiralty.ui.resources.Strings.Empty;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.*;

public class ShipRosterPanel extends JPanel implements AdmiralUI, RosterChangeListener {

    @Serial
    private static final long serialVersionUID = -6255733882357549115L;
    private final Action actionAllMaintenanceToActive = new AllMaintenanceToActiveAction();
    private final Action actionAllActiveToMaintenance = new AllActiveToMaintenanceAction();
    private final Action actionMaintenanceToActive = new MaintenanceToActiveAction();
    private final Action actionActiveToMaintenance = new ActiveToMaintenanceAction();
    private final Action actionAddShip = new AddActiveShipAction();
    private final Action actionRemoveShip = new RemoveActiveShipAction();
    protected Admiral admiral;
    protected RosterCardListModel modelActive;
    protected RosterCardListModel modelMaintenance;
    protected JLabel lblActive;
    protected JLabel lblMaintenance;
    protected JList<RosterCard> lstActive;
    protected JList<RosterCard> lstMaintenance;

    public ShipRosterPanel(Admiral admiral) {
        this();
        setAdmiral(admiral);
    }

    /**
     * Create the panel.
     */
    private ShipRosterPanel() {
        GridBagLayout gbl_panel = new GridBagLayout();
        gbl_panel.columnWidths = new int[] { 0, 0, 0, 0, 0 };
        gbl_panel.rowHeights = new int[] { 0, 0, 0, 0, 0, 0, 0, 0 };
        gbl_panel.columnWeights = new double[] { 1.0, 1.0, 0.0, 0.0, Double.MIN_VALUE };
        gbl_panel.rowWeights = new double[] { 0.0, 1.0, 1.0, 1.0, 0.0, 0.0, 1.0, Double.MIN_VALUE };
        setLayout(gbl_panel);

        lblActive = new JLabel(LabelActiveShips);
        GridBagConstraints gbc_lblActive = new GridBagConstraints();
        gbc_lblActive.fill = GridBagConstraints.HORIZONTAL;
        gbc_lblActive.anchor = GridBagConstraints.WEST;
        gbc_lblActive.insets = new Insets(5, 5, 5, 5);
        gbc_lblActive.gridx = 0;
        gbc_lblActive.gridy = 0;
        add(lblActive, gbc_lblActive);

        lblMaintenance = new JLabel(LabelMaintenanceShips);
        GridBagConstraints gbc_lblMaintenance = new GridBagConstraints();
        gbc_lblMaintenance.fill = GridBagConstraints.HORIZONTAL;
        gbc_lblMaintenance.anchor = GridBagConstraints.WEST;
        gbc_lblMaintenance.insets = new Insets(5, 5, 5, 5);
        gbc_lblMaintenance.gridx = 2;
        gbc_lblMaintenance.gridy = 0;
        add(lblMaintenance, gbc_lblMaintenance);

        JScrollPane sclActive = new JScrollPane();
        GridBagConstraints gbc_sclActive = new GridBagConstraints();
        gbc_sclActive.weighty = 10.0;
        gbc_sclActive.weightx = 100.0;
        gbc_sclActive.fill = GridBagConstraints.BOTH;
        gbc_sclActive.gridheight = 6;
        gbc_sclActive.insets = new Insets(5, 5, 5, 5);
        gbc_sclActive.gridx = 0;
        gbc_sclActive.gridy = 1;
        add(sclActive, gbc_sclActive);

        modelActive = new RosterCardListModel();
        lstActive = new JList<RosterCard>(modelActive);
        lstActive.addMouseListener(new MouseAdapter() {
            /**
             * Moves a double-clicked Active card to Maintenance in one Admiral operation.
             */
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = lstActive.locationToIndex(e.getPoint());
                    RosterCard card = modelActive.getElementAt(index);
                    lstActive.clearSelection();
                    admiral.moveReusableCards(List.of(card), RosterState.MAINTENANCE);
                }
            }
        });
        lstActive.setCellRenderer(RosterCardCellRenderer.shipCards());
        sclActive.setViewportView(lstActive);

        JLabel lblTop = new JLabel("");
        GridBagConstraints gbc_lblTop = new GridBagConstraints();
        gbc_lblTop.weighty = 100.0;
        gbc_lblTop.weightx = 1.0;
        gbc_lblTop.insets = new Insets(0, 0, 5, 5);
        gbc_lblTop.gridx = 1;
        gbc_lblTop.gridy = 1;
        add(lblTop, gbc_lblTop);

        JScrollPane sclMaintenance = new JScrollPane();
        GridBagConstraints gbc_sclMaintenance = new GridBagConstraints();
        gbc_sclMaintenance.weighty = 10.0;
        gbc_sclMaintenance.weightx = 100.0;
        gbc_sclMaintenance.fill = GridBagConstraints.BOTH;
        gbc_sclMaintenance.gridheight = 6;
        gbc_sclMaintenance.insets = new Insets(5, 5, 5, 5);
        gbc_sclMaintenance.gridx = 2;
        gbc_sclMaintenance.gridy = 1;
        add(sclMaintenance, gbc_sclMaintenance);

        modelMaintenance = new RosterCardListModel();
        lstMaintenance = new JList<RosterCard>(modelMaintenance);
        lstMaintenance.addMouseListener(new MouseAdapter() {
            /**
             * Moves a double-clicked Maintenance card to Active in one Admiral operation.
             */
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = lstMaintenance.locationToIndex(e.getPoint());
                    RosterCard card = modelMaintenance.getElementAt(index);
                    lstMaintenance.clearSelection();
                    admiral.moveReusableCards(List.of(card), RosterState.ACTIVE);
                }
            }
        });
        lstMaintenance.setCellRenderer(RosterCardCellRenderer.shipCards());
        sclMaintenance.setViewportView(lstMaintenance);

        JPanel pnlButtons = new JPanel();
        pnlButtons.setBorder(null);
        GridBagConstraints gbc_pnlButtons = new GridBagConstraints();
        gbc_pnlButtons.weighty = 100.0;
        gbc_pnlButtons.weightx = 1.0;
        gbc_pnlButtons.insets = new Insets(5, 0, 0, 5);
        gbc_pnlButtons.fill = GridBagConstraints.HORIZONTAL;
        gbc_pnlButtons.anchor = GridBagConstraints.NORTH;
        gbc_pnlButtons.gridheight = 6;
        gbc_pnlButtons.gridx = 3;
        gbc_pnlButtons.gridy = 1;
        add(pnlButtons, gbc_pnlButtons);
        GridBagLayout gbl_pnlButtons = new GridBagLayout();
        gbl_pnlButtons.columnWidths = new int[] { 0, 0 };
        gbl_pnlButtons.rowHeights = new int[] { 0, 0, 0 };
        gbl_pnlButtons.columnWeights = new double[] { 0.0, Double.MIN_VALUE };
        gbl_pnlButtons.rowWeights = new double[] { 0.0, 0.0, Double.MIN_VALUE };
        pnlButtons.setLayout(gbl_pnlButtons);

        JButton btnAddShip = new JButton(actionAddShip);
        GridBagConstraints gbc_btnAddShip = new GridBagConstraints();
        gbc_btnAddShip.fill = GridBagConstraints.HORIZONTAL;
        gbc_btnAddShip.insets = new Insets(0, 0, 5, 0);
        gbc_btnAddShip.gridx = 0;
        gbc_btnAddShip.gridy = 0;
        pnlButtons.add(btnAddShip, gbc_btnAddShip);

        JButton btnRemoveShip = new JButton(actionRemoveShip);
        GridBagConstraints gbc_btnRemoveShip = new GridBagConstraints();
        gbc_btnRemoveShip.fill = GridBagConstraints.HORIZONTAL;
        gbc_btnRemoveShip.gridx = 0;
        gbc_btnRemoveShip.gridy = 1;
        pnlButtons.add(btnRemoveShip, gbc_btnRemoveShip);

        JButton btnAllActive = new JButton(actionAllMaintenanceToActive);
        GridBagConstraints gbc_btnAllActive = new GridBagConstraints();
        gbc_btnAllActive.weighty = 1.0;
        gbc_btnAllActive.weightx = 1.0;
        gbc_btnAllActive.fill = GridBagConstraints.HORIZONTAL;
        gbc_btnAllActive.insets = new Insets(0, 0, 5, 5);
        gbc_btnAllActive.gridx = 1;
        gbc_btnAllActive.gridy = 2;
        add(btnAllActive, gbc_btnAllActive);

        JButton btnActive = new JButton(actionMaintenanceToActive);
        GridBagConstraints gbc_btnActive = new GridBagConstraints();
        gbc_btnActive.weighty = 1.0;
        gbc_btnActive.weightx = 1.0;
        gbc_btnActive.fill = GridBagConstraints.HORIZONTAL;
        gbc_btnActive.insets = new Insets(0, 0, 5, 5);
        gbc_btnActive.gridx = 1;
        gbc_btnActive.gridy = 3;
        add(btnActive, gbc_btnActive);

        JButton btnMaintenance = new JButton(actionActiveToMaintenance);
        GridBagConstraints gbc_btnMaintenance = new GridBagConstraints();
        gbc_btnMaintenance.weighty = 1.0;
        gbc_btnMaintenance.weightx = 1.0;
        gbc_btnMaintenance.fill = GridBagConstraints.HORIZONTAL;
        gbc_btnMaintenance.insets = new Insets(0, 0, 5, 5);
        gbc_btnMaintenance.gridx = 1;
        gbc_btnMaintenance.gridy = 4;
        add(btnMaintenance, gbc_btnMaintenance);

        JButton btnAllMaintenance = new JButton(actionAllActiveToMaintenance);
        GridBagConstraints gbc_btnAllMaintenance = new GridBagConstraints();
        gbc_btnAllMaintenance.weighty = 1.0;
        gbc_btnAllMaintenance.weightx = 1.0;
        gbc_btnAllMaintenance.fill = GridBagConstraints.HORIZONTAL;
        gbc_btnAllMaintenance.insets = new Insets(0, 0, 5, 5);
        gbc_btnAllMaintenance.gridx = 1;
        gbc_btnAllMaintenance.gridy = 5;
        add(btnAllMaintenance, gbc_btnAllMaintenance);

        JLabel lblBottom = new JLabel("");
        GridBagConstraints gbc_lblBottom = new GridBagConstraints();
        gbc_lblBottom.weighty = 100.0;
        gbc_lblBottom.weightx = 1.0;
        gbc_lblBottom.insets = new Insets(0, 0, 5, 5);
        gbc_lblBottom.gridx = 1;
        gbc_lblBottom.gridy = 6;
        add(lblBottom, gbc_lblBottom);
    }

    @Override
    public Admiral getAdmiral() {
        return admiral;
    }

    /**
     * Selects the Admiral whose immutable Roster views drive both reusable-card
     * lists.
     * Listener ownership follows the selected Admiral so later commits refresh this
     * panel once.
     *
     * @param admiral selected Admiral, or {@code null} to clear both lists
     */
    @Override
    public void setAdmiral(Admiral admiral) {
        if (this.admiral != null) {
            this.admiral.removeRosterChangeListener(this);
        }
        this.admiral = admiral;
        refreshRoster(admiral == null ? null : admiral.getRoster());
        if (this.admiral != null) {
            admiral.addRosterChangeListener(this);
        }
    }

    /**
     * Refreshes both reusable-card lists from the single post-commit view delivered
     * by Admiral.
     *
     * @param change committed Roster transition
     */
    @Override
    public void rosterChanged(RosterChange change) {
        refreshRoster(change.getAfter());
    }

    /**
     * Applies one immutable Roster revision to both models and their quantity
     * labels.
     *
     * @param roster complete Roster view, or {@code null} to clear the panel
     */
    private void refreshRoster(RosterView roster) {
        List<RosterCard> activeCards = roster == null ? List.of() : roster.getActiveCards();
        List<RosterCard> maintenanceCards = roster == null ? List.of() : roster.getMaintenanceCards();
        modelActive.setCards(activeCards);
        modelMaintenance.setCards(maintenanceCards);
        lblActive.setText(String.format(HtmlActiveShips, activeCards.size()));
        lblMaintenance.setText(String.format(HtmlMaintenanceShips, maintenanceCards.size()));
    }

    private class AllMaintenanceToActiveAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = -509981822658289573L;

        public AllMaintenanceToActiveAction() {
            super(Empty, Images.ICON_LEFT_ALL);
            putValue(SHORT_DESCRIPTION, DescAllMaintenanceToActive);
        }

        /** Moves every Maintenance card to Active in one Admiral operation. */
        @Override
        public void actionPerformed(ActionEvent e) {
            List<RosterCard> cards = admiral.getRoster().getMaintenanceCards();
            if (!cards.isEmpty()) {
                admiral.moveReusableCards(cards, RosterState.ACTIVE);
            }
        }
    }

    private class AllActiveToMaintenanceAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = -7137558996349465891L;

        public AllActiveToMaintenanceAction() {
            super(Empty, Images.ICON_RIGHT_ALL);
            putValue(SHORT_DESCRIPTION, DescAllActiveToMaintenance);
        }

        /** Moves every Active card to Maintenance in one Admiral operation. */
        @Override
        public void actionPerformed(ActionEvent e) {
            List<RosterCard> cards = admiral.getRoster().getActiveCards();
            if (!cards.isEmpty()) {
                admiral.moveReusableCards(cards, RosterState.MAINTENANCE);
            }
        }
    }

    private class MaintenanceToActiveAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = 9205828368701721872L;

        public MaintenanceToActiveAction() {
            super(Empty, Images.ICON_LEFT_ONE);
            putValue(SHORT_DESCRIPTION, DescMaintenanceToActive);
        }

        /** Moves the selected Maintenance cards to Active in one Admiral operation. */
        @Override
        public void actionPerformed(ActionEvent e) {
            List<RosterCard> cards = lstMaintenance.getSelectedValuesList();
            if (!cards.isEmpty()) {
                admiral.moveReusableCards(cards, RosterState.ACTIVE);
            }
            lstActive.setSelectedIndices(new int[0]);
            lstMaintenance.setSelectedIndices(new int[0]);
        }
    }

    private class ActiveToMaintenanceAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = -3757003998462960644L;

        public ActiveToMaintenanceAction() {
            super(Empty, Images.ICON_RIGHT_ONE);
            putValue(SHORT_DESCRIPTION, DescActiveToMaintenance);
        }

        /** Moves the selected Active cards to Maintenance in one Admiral operation. */
        @Override
        public void actionPerformed(ActionEvent e) {
            List<RosterCard> cards = lstActive.getSelectedValuesList();
            if (!cards.isEmpty()) {
                admiral.moveReusableCards(cards, RosterState.MAINTENANCE);
            }
            lstActive.setSelectedIndices(new int[0]);
            lstMaintenance.setSelectedIndices(new int[0]);
        }
    }

    private class AddActiveShipAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = 2156513045423271256L;

        public AddActiveShipAction() {
            super(LabelShip, Images.ICON_ADD);
            putValue(SHORT_DESCRIPTION, DescAddActiveShips);
        }

        /** Adds all selected reusable Ships to Active in one Admiral operation. */
        @Override
        public void actionPerformed(ActionEvent e) {
            Window window = SwingUtilities.getWindowAncestor((Component) e.getSource());
            TreeSet<Ship> inputShips = new TreeSet<Ship>(App.gameData().ships());
            for (RosterCard card : admiral.getRoster().getReusableCards()) {
                inputShips.remove(card.getShip());
            }
            List<Ship> ships = ShipSelectionPanel.dialogActiveShips(window, admiral.getFaction(), inputShips,
                    TitleAddActiveShips);
            if (!ships.isEmpty()) {
                admiral.addReusableShips(ships, RosterState.ACTIVE);
            }
        }
    }

    private class RemoveActiveShipAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = -4040927572982355707L;

        public RemoveActiveShipAction() {
            super(LabelShip, Images.ICON_REMOVE);
            putValue(SHORT_DESCRIPTION, DescRemoveActiveShips);
        }

        /** Removes the exact selected reusable cards in one Admiral operation. */
        @Override
        public void actionPerformed(ActionEvent e) {
            Window window = SwingUtilities.getWindowAncestor((Component) e.getSource());
            RosterView roster = admiral.getRoster();
            List<RosterCard> selectedCards = ShipListPanel.dialogRosterCards(
                    window,
                    roster.getReusableCards(),
                    TitleRemoveActiveShips);
            if (!selectedCards.isEmpty()) {
                admiral.removeReusableCards(selectedCards);
            }
        }
    }

}
