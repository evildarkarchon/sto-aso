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
package com.kor.admiralty.ui;

import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.Admirals;
import com.kor.admiralty.beans.ShipUsageRow;
import com.kor.admiralty.enums.ShipUsageSortOrder;
import com.kor.admiralty.ui.resources.ShipIconFactory;
import com.kor.admiralty.ui.resources.Swing;
import com.kor.admiralty.ui.shipfilter.ShipFilterView;
import com.kor.admiralty.ui.shipfilter.ShipFilterViews;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static com.kor.admiralty.ui.resources.Strings.ShipStatistics.*;

/** Usage window content shared by the native frame and headless consumer tests. */
final class ShipUsagePanel extends JPanel {

    @Serial
    private static final long serialVersionUID = -7842523811419803589L;
    private final ShipViewAction actionShipView = new ShipViewAction();
    private final Action actionDefaultSort = new DefaultSortAction();
    private final Action actionMostUsed = new MostUsedAction();
    private final Action actionLeastUsed = new LeastUsedAction();
    private final Action actionClearUsageData = new ClearUsageDataAction();
    private final Admirals admirals;
    private final ShipFilterView<ShipUsageRow, ShipUsageSortOrder> pnlShips;
    private final JComboBox<String> cbxAdmirals;

    /**
     * Builds usage controls and the initial Most Used projection on the EDT.
     *
     * @param admirals Admirals whose current Rosters and history are represented
     * @param iconRenderer existing artwork boundary for usage rows
     * @throws IllegalStateException if constructed outside the event-dispatch thread
     * @throws NullPointerException if a dependency is null
     */
    ShipUsagePanel(Admirals admirals, ShipIconFactory iconRenderer) {
        Swing.requireEventDispatchThread("construct Ship usage content");
        this.admirals = Objects.requireNonNull(admirals, "admirals");
        LineBorder border = new LineBorder(getBackground().darker(), 1, true);

        setLayout(new BorderLayout(5, 5));

        JPanel pnlControls = new JPanel();
        add(pnlControls, BorderLayout.NORTH);
        GridBagLayout gbl_pnlControls = new GridBagLayout();
        gbl_pnlControls.columnWidths = new int[]{0, 0};
        gbl_pnlControls.rowHeights = new int[]{0};
        gbl_pnlControls.columnWeights = new double[]{0.0, 0.0};
        gbl_pnlControls.rowWeights = new double[]{0.0};
        pnlControls.setLayout(gbl_pnlControls);

        JPanel pnlAdmirals = new JPanel();
        pnlAdmirals.setBorder(new TitledBorder(border, LabelAdmirals, TitledBorder.LEADING, TitledBorder.TOP, null,
                new Color(0, 0, 0)));
        GridBagConstraints gbc_pnlAdmirals = new GridBagConstraints();
        gbc_pnlAdmirals.weighty = 1.0;
        gbc_pnlAdmirals.weightx = 9.0;
        gbc_pnlAdmirals.fill = GridBagConstraints.BOTH;
        gbc_pnlAdmirals.insets = new Insets(0, 0, 0, 5);
        gbc_pnlAdmirals.gridx = 0;
        gbc_pnlAdmirals.gridy = 0;
        pnlControls.add(pnlAdmirals, gbc_pnlAdmirals);
        GridBagLayout gbl_pnlAdmirals = new GridBagLayout();
        gbl_pnlAdmirals.columnWidths = new int[]{0, 0, 0};
        gbl_pnlAdmirals.rowHeights = new int[]{0};
        gbl_pnlAdmirals.columnWeights = new double[]{0.0, 0.0, Double.MIN_VALUE};
        gbl_pnlAdmirals.rowWeights = new double[]{0.0};
        pnlAdmirals.setLayout(gbl_pnlAdmirals);

        cbxAdmirals = new JComboBox<String>();
        GridBagConstraints gbc_cbxAdmirals = new GridBagConstraints();
        gbc_cbxAdmirals.insets = new Insets(0, 5, 5, 0);
        gbc_cbxAdmirals.weightx = 1.0;
        gbc_cbxAdmirals.fill = GridBagConstraints.HORIZONTAL;
        gbc_cbxAdmirals.gridx = 0;
        gbc_cbxAdmirals.gridy = 0;
        pnlAdmirals.add(cbxAdmirals, gbc_cbxAdmirals);

        JButton btnClearUsageData = new JButton();
        btnClearUsageData.setAction(actionClearUsageData);
        GridBagConstraints gbc_btnClearUsageData = new GridBagConstraints();
        gbc_btnClearUsageData.insets = new Insets(0, 1, 5, 5);
        gbc_btnClearUsageData.gridx = 1;
        gbc_btnClearUsageData.gridy = 0;
        pnlAdmirals.add(btnClearUsageData, gbc_btnClearUsageData);

        JPanel pnlSortBy = new JPanel();
        pnlSortBy.setBorder(new TitledBorder(border, "Sort by...", TitledBorder.LEADING, TitledBorder.TOP, null,
                new Color(0, 0, 0)));
        GridBagConstraints gbc_pnlSortBy = new GridBagConstraints();
        gbc_pnlSortBy.weighty = 1.0;
        gbc_pnlSortBy.weightx = 1.0;
        gbc_pnlSortBy.fill = GridBagConstraints.BOTH;
        gbc_pnlSortBy.gridx = 1;
        gbc_pnlSortBy.gridy = 0;
        pnlControls.add(pnlSortBy, gbc_pnlSortBy);

        ButtonGroup grpSortBy = new ButtonGroup();
        pnlSortBy.setLayout(new GridLayout(0, 3, 0, 0));
        JToggleButton btnDefault = new JToggleButton(actionDefaultSort);
        pnlSortBy.add(btnDefault);
        grpSortBy.add(btnDefault);

        JToggleButton btnMostUsed = new JToggleButton(actionMostUsed);
        btnMostUsed.setSelected(true);
        pnlSortBy.add(btnMostUsed);
        grpSortBy.add(btnMostUsed);

        JToggleButton btnLeastUsed = new JToggleButton(actionLeastUsed);
        pnlSortBy.add(btnLeastUsed);
        grpSortBy.add(btnLeastUsed);

        // Install the complete usage preset before any history is published.
        pnlShips = new ShipFilterViews(iconRenderer).shipUsage(
                admirals.getShipUsageRows(Admirals.toArray(admirals.getAdmirals())));
        add(pnlShips);

        cbxAdmirals.addItem(LabelAllAdmirals);
        cbxAdmirals.addItem(LabelFederationAdmirals);
        cbxAdmirals.addItem(LabelKlingonAdmirals);
        cbxAdmirals.addItem(LabelRomulanAdmirals);
        cbxAdmirals.addItem(LabelJemHadarAdmirals);
        for (Admiral admiral : admirals.getAdmirals()) {
            cbxAdmirals.addItem(admiral.getName());
        }
        // Population selects the first item; listen only after the initial projection
        // so construction never publishes a redundant intermediate history snapshot.
        cbxAdmirals.addActionListener(actionShipView);
    }

    /** Refreshes the represented Admirals while retaining the chosen filter and order. */
    void refresh() {
        Swing.requireEventDispatchThread("refresh Ship usage");
        setShipView((String) cbxAdmirals.getSelectedItem());
    }

    /** Resolves a named Admiral or the established faction group into one snapshot. */
    private void setShipView(String name) {
        if (name == null || name.equals(LabelAllAdmirals)) {
            setShipViewAll();
        } else if (name.equals(LabelFederationAdmirals)) {
            setShipViewFederation();
        } else if (name.equals(LabelKlingonAdmirals)) {
            setShipViewKlingon();
        } else if (name.equals(LabelRomulanAdmirals)) {
            setShipViewRomulan();
        } else if (name.equals(LabelJemHadarAdmirals)) {
            setShipViewJemHadar();
        } else {
            Admiral admiral = admirals.findByName(name);
            setShipView(admiral);
        }
    }

    /** Presents usage across every Admiral. */
    private void setShipViewAll() {
        setShipView(admirals.getAdmirals());
    }

    /** Presents usage for Federation-aligned Admirals. */
    private void setShipViewFederation() {
        setShipView(admirals.getFederationAdmirals());
    }

    /** Presents usage for Klingon-aligned Admirals. */
    private void setShipViewKlingon() {
        setShipView(admirals.getKlingonAdmirals());
    }

    /** Presents usage for both Romulan alignments. */
    private void setShipViewRomulan() {
        setShipView(admirals.getRomulanAdmirals());
    }

    /** Presents usage for both Jem'Hadar alignments. */
    private void setShipViewJemHadar() {
        setShipView(admirals.getJemHadarAdmirals());
    }

    /**
     * Projects one selected Admiral collection into immutable Ship Statistics rows.
     *
     * @param collection selected Admirals whose current Rosters and history form
     *                   the view
     */
    private void setShipView(Collection<Admiral> collection) {
        setShipView(Admirals.toArray(collection));
    }

    /**
     * Replaces the visible statistics snapshot without mutating canonical GameData
     * Ships.
     *
     * @param array selected Admirals whose current Rosters and deployment history
     *              form the view
     */
    private void setShipView(Admiral... array) {
        List<ShipUsageRow> rows = admirals.getShipUsageRows(array);
        pnlShips.present(rows);
    }

    /** Clears history for every Admiral and refreshes the current rows. */
    private void clearAllUsageData() {
        clearUsageData(admirals.getAdmirals());
    }

    /** Clears history for Federation-aligned Admirals. */
    private void clearFederationUsageData() {
        clearUsageData(admirals.getFederationAdmirals());
    }

    /** Clears history for Klingon-aligned Admirals. */
    private void clearKlingonUsageData() {
        clearUsageData(admirals.getKlingonAdmirals());
    }

    /** Clears history for both Romulan alignments. */
    private void clearRomulanUsageData() {
        clearUsageData(admirals.getRomulanAdmirals());
    }

    /** Clears history for both Jem'Hadar alignments. */
    private void clearJemHadarUsageData() {
        clearUsageData(admirals.getJemHadarAdmirals());
    }

    /** Clears only the represented Admirals and publishes their replacement rows. */
    private void clearUsageData(Collection<Admiral> collection) {
        clearUsageData(Admirals.toArray(collection));
    }

    /** Clears only the represented Admirals and publishes their replacement rows. */
    private void clearUsageData(Admiral... array) {
        for (Admiral admiral : array) {
            admiral.clearUsage();
        }
        setShipView(array);
    }

    private final class DefaultSortAction extends AbstractAction {

        @Serial
        private static final long serialVersionUID = 2591067670029290567L;

        /** Creates the established Default control action. */
        public DefaultSortAction() {
            super(LabelDefaultSort);
            putValue(SHORT_DESCRIPTION, DescDefaultSort);
        }

        /** Applies the player's control choice on the event-dispatch thread. */
        public void actionPerformed(ActionEvent e) {
            pnlShips.orderBy(ShipUsageSortOrder.Default);
        }
    }

    private final class MostUsedAction extends AbstractAction {

        @Serial
        private static final long serialVersionUID = -8939959467353282880L;

        /** Creates the established Most Used control action. */
        public MostUsedAction() {
            super(LabelMostUsed);
            putValue(SHORT_DESCRIPTION, DescMostUsed);
        }

        /** Applies the player's control choice on the event-dispatch thread. */
        public void actionPerformed(ActionEvent e) {
            pnlShips.orderBy(ShipUsageSortOrder.MostUsed);
        }
    }

    private final class LeastUsedAction extends AbstractAction {

        @Serial
        private static final long serialVersionUID = -4791903586696391645L;

        /** Creates the established Least Used control action. */
        public LeastUsedAction() {
            super(LabelLeastUsed);
            putValue(SHORT_DESCRIPTION, DescLeastUsed);
        }

        /** Applies the player's control choice on the event-dispatch thread. */
        public void actionPerformed(ActionEvent e) {
            pnlShips.orderBy(ShipUsageSortOrder.LeastUsed);
        }
    }

    private final class ClearUsageDataAction extends AbstractAction {

        @Serial
        private static final long serialVersionUID = -2506691204971648770L;

        /** Creates the established Clear usage data control action. */
        public ClearUsageDataAction() {
            super(LabelClearUsageData);
            putValue(SHORT_DESCRIPTION, DescClearUsageData);
        }

        /** Applies the player's control choice on the event-dispatch thread. */
        public void actionPerformed(ActionEvent e) {
            String title = TitleClearUsageData;
            String name = cbxAdmirals.getSelectedItem().toString();
            String message = String.format(MsgClearUsageData, name);
            int result = JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(ShipUsagePanel.this), message, title,
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (result == JOptionPane.YES_OPTION) {
                if (name == null || name.equals(LabelAllAdmirals)) {
                    clearAllUsageData();
                } else if (name.equals(LabelFederationAdmirals)) {
                    clearFederationUsageData();
                } else if (name.equals(LabelKlingonAdmirals)) {
                    clearKlingonUsageData();
                } else if (name.equals(LabelRomulanAdmirals)) {
                    clearRomulanUsageData();
                } else if (name.equals(LabelJemHadarAdmirals)) {
                    clearJemHadarUsageData();
                } else {
                    Admiral admiral = admirals.findByName(name);
                    clearUsageData(admiral);
                }
            }
        }
    }

    private final class ShipViewAction implements ActionListener {

        /** Applies the player's control choice on the event-dispatch thread. */
        @Override
        public void actionPerformed(ActionEvent e) {
            refresh();
        }

    }

}
