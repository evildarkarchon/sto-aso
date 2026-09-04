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

import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.RosterView;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.enums.PlayerFaction;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.ui.RosterCardSelections;
import com.kor.admiralty.ui.models.RosterCardListModel;
import com.kor.admiralty.ui.renderers.RosterCardCellRenderer;
import com.kor.admiralty.ui.resources.Images;
import com.kor.admiralty.ui.resources.ShipIconFactory;
import com.kor.admiralty.ui.resources.Swing;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.Serial;
import java.util.List;
import java.util.Objects;

import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.*;
import static com.kor.admiralty.ui.resources.Strings.Empty;

public class OneTimeShipPanel extends JPanel {

    @Serial
    private static final long serialVersionUID = -8838468996200841140L;
    private final Action actionAddOneTimeShip = new AddOneTimeShipAction();
    private final Action actionRemoveOneTimeShip = new RemoveOneTimeShipAction();
    private final GameData gameData;
    private final ShipIconFactory iconRenderer;
    private final RosterSelectionDialog rosterSelectionDialog;
    private final Actions actions;
    protected PlayerFaction faction;
    protected RosterView rosterView;
    protected RosterCardListModel uiModel;
    protected JLabel lblOnetimeShips;
    protected JList<RosterCard> uiList;

    /**
     * Creates One-Time Ship presentation with explicit lookup, artwork, and intent
     * dependencies.
     *
     * @param gameData     reference data used by One-Time Ship selection
     * @param iconRenderer renderer used by lists and selection dialogs
     * @param actions      root-owned mutation boundary for reported user intent
     * @throws NullPointerException if a dependency is {@code null}
     */
    OneTimeShipPanel(GameData gameData, ShipIconFactory iconRenderer, Actions actions) {
        this(gameData, iconRenderer, RosterSelectionDialog.swing(), actions);
    }

    /**
     * Creates One-Time Ship presentation with a supplied modal-selection adapter.
     *
     * @param gameData              reference data used by One-Time Ship selection
     * @param iconRenderer          renderer used by lists and selection dialogs
     * @param rosterSelectionDialog One-Time Ship add/remove selection seam
     * @param actions               root-owned mutation boundary for reported user intent
     * @throws NullPointerException if a dependency is {@code null}
     */
    OneTimeShipPanel(
            GameData gameData,
            ShipIconFactory iconRenderer,
            RosterSelectionDialog rosterSelectionDialog,
            Actions actions) {
        this.gameData = Objects.requireNonNull(gameData, "gameData");
        this.iconRenderer = Objects.requireNonNull(iconRenderer, "iconRenderer");
        this.rosterSelectionDialog = Objects.requireNonNull(rosterSelectionDialog, "rosterSelectionDialog");
        this.actions = Objects.requireNonNull(actions, "actions");
        GridBagLayout gbl_panel = new GridBagLayout();
        gbl_panel.columnWidths = new int[]{0, 0, 0};
        gbl_panel.rowHeights = new int[]{0, 0, 0, 0, 0};
        gbl_panel.columnWeights = new double[]{1.0, 1.0, Double.MIN_VALUE};
        gbl_panel.rowWeights = new double[]{0.0, 1.0, 0.0, 1.0, Double.MIN_VALUE};
        setLayout(gbl_panel);

        lblOnetimeShips = new JLabel(LabelOneTimeShips);
        GridBagConstraints gbc_lblOnetimeShips = new GridBagConstraints();
        gbc_lblOnetimeShips.fill = GridBagConstraints.HORIZONTAL;
        gbc_lblOnetimeShips.gridwidth = 2;
        gbc_lblOnetimeShips.insets = new Insets(5, 5, 0, 0);
        gbc_lblOnetimeShips.gridx = 0;
        gbc_lblOnetimeShips.gridy = 0;
        add(lblOnetimeShips, gbc_lblOnetimeShips);

        JScrollPane sclOneTimeShips = new JScrollPane();
        RosterScrolling.configureOneTimeCards(sclOneTimeShips);
        GridBagConstraints gbc_sclOneTimeShips = new GridBagConstraints();
        gbc_sclOneTimeShips.weighty = 10.0;
        gbc_sclOneTimeShips.weightx = 5.0;
        gbc_sclOneTimeShips.fill = GridBagConstraints.BOTH;
        gbc_sclOneTimeShips.gridheight = 3;
        gbc_sclOneTimeShips.insets = new Insets(5, 5, 5, 5);
        gbc_sclOneTimeShips.gridx = 0;
        gbc_sclOneTimeShips.gridy = 1;
        add(sclOneTimeShips, gbc_sclOneTimeShips);

        uiModel = new RosterCardListModel();
        uiList = new JList<RosterCard>(uiModel);
        uiList.setCellRenderer(RosterCardCellRenderer.shipCards(iconRenderer));
        sclOneTimeShips.setViewportView(uiList);

        JButton btnAddOneTime = new JButton(actionAddOneTimeShip);
        GridBagConstraints gbc_btnAddOneTime = new GridBagConstraints();
        gbc_btnAddOneTime.weighty = 1.0;
        gbc_btnAddOneTime.weightx = 1.0;
        gbc_btnAddOneTime.fill = GridBagConstraints.HORIZONTAL;
        gbc_btnAddOneTime.insets = new Insets(5, 0, 5, 5);
        gbc_btnAddOneTime.gridx = 1;
        gbc_btnAddOneTime.gridy = 1;
        add(btnAddOneTime, gbc_btnAddOneTime);

        JButton btnRemoveOneTime = new JButton(actionRemoveOneTimeShip);
        GridBagConstraints gbc_btnRemoveOneTime = new GridBagConstraints();
        gbc_btnRemoveOneTime.weighty = 1.0;
        gbc_btnRemoveOneTime.weightx = 1.0;
        gbc_btnRemoveOneTime.fill = GridBagConstraints.HORIZONTAL;
        gbc_btnRemoveOneTime.insets = new Insets(0, 0, 5, 5);
        gbc_btnRemoveOneTime.gridx = 1;
        gbc_btnRemoveOneTime.gridy = 2;
        add(btnRemoveOneTime, gbc_btnRemoveOneTime);

        JLabel lblEmpty = new JLabel(Empty);
        GridBagConstraints gbc_lblEmpty = new GridBagConstraints();
        gbc_lblEmpty.weighty = 1000.0;
        gbc_lblEmpty.gridx = 1;
        gbc_lblEmpty.gridy = 3;
        add(lblEmpty, gbc_lblEmpty);
    }

    /**
     * Applies one root-supplied identity and Roster projection without selecting or
     * subscribing to an Admiral.
     *
     * @param view complete immutable workspace projection
     * @throws NullPointerException  if {@code view} is {@code null}
     * @throws IllegalStateException if called outside the Swing event thread
     */
    void render(AdmiralWorkspaceView view) {
        Swing.requireEventDispatchThread("project One-Time Ship state");
        Objects.requireNonNull(view, "view");
        faction = view.faction();
        rosterView = view.roster();
        List<RosterCard> cards = rosterView.getOneTimeCards();
        uiModel.setCards(cards);
        lblOnetimeShips.setText(String.format(HtmlOneTimeShips, cards.size()));
    }

    /**
     * Receives One-Time Ship user intent without exposing the bound Admiral.
     */
    interface Actions {

        /**
         * Applies one signed quantity adjustment per supplied Ship occurrence.
         */
        void adjustOneTimeShipQuantities(List<Ship> ships, int adjustmentPerOccurrence);
    }

    private class AddOneTimeShipAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = -9000567166027604196L;

        public AddOneTimeShipAction() {
            super(LabelShip, Images.ICON_ADD);
            putValue(SHORT_DESCRIPTION, DescAddOneTimeShips);
        }

        /**
         * Increments every selected One-Time Ship type in one Admiral operation.
         */
        @Override
        public void actionPerformed(ActionEvent e) {
            Window window = SwingUtilities.getWindowAncestor((Component) e.getSource());
            List<Ship> ships = rosterSelectionDialog.chooseOneTimeShips(
                    window,
                    faction,
                    gameData.ships(),
                    iconRenderer);
            if (!ships.isEmpty()) {
                actions.adjustOneTimeShipQuantities(ships, 1);
            }
        }
    }

    private class RemoveOneTimeShipAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = -5773265252031585211L;

        public RemoveOneTimeShipAction() {
            super(LabelShip, Images.ICON_REMOVE);
            putValue(SHORT_DESCRIPTION, DescRemoveOneTimeShips);
        }

        /**
         * Decrements every selected One-Time Ship type in one Admiral operation.
         */
        @Override
        public void actionPerformed(ActionEvent e) {
            Window window = SwingUtilities.getWindowAncestor((Component) e.getSource());
            List<RosterCard> cards = rosterSelectionDialog.chooseRosterCards(
                    window,
                    RosterCardSelections.oneTimeShipTypes(rosterView),
                    iconRenderer,
                    TitleRemoveOneTimeShips);
            if (!cards.isEmpty()) {
                actions.adjustOneTimeShipQuantities(RosterCardSelections.ships(cards), -1);
            }
        }
    }

}
