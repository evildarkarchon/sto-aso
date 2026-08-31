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

import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.RosterChange;
import com.kor.admiralty.beans.RosterChangeListener;
import com.kor.admiralty.beans.RosterView;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.ui.ShipListPanel;
import com.kor.admiralty.ui.RosterCardSelections;
import com.kor.admiralty.ui.ShipSelectionPanel;
import com.kor.admiralty.ui.models.RosterCardListModel;
import com.kor.admiralty.ui.renderers.RosterCardCellRenderer;
import com.kor.admiralty.ui.resources.Images;
import com.kor.admiralty.ui.resources.ShipIconFactory;

import java.awt.GridBagLayout;
import javax.swing.JLabel;
import javax.swing.JList;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.io.Serial;
import java.util.List;
import java.util.Objects;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.JButton;
import javax.swing.AbstractAction;
import javax.swing.Action;

import static com.kor.admiralty.ui.resources.Strings.Empty;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.*;

public class OneTimeShipPanel extends JPanel implements AdmiralUI, RosterChangeListener {

    @Serial
    private static final long serialVersionUID = -8838468996200841140L;
    private final Action actionAddOneTimeShip = new AddOneTimeShipAction();
    private final Action actionRemoveOneTimeShip = new RemoveOneTimeShipAction();
    private final GameData gameData;
    private final ShipIconFactory iconRenderer;
    protected Admiral admiral;
    protected RosterCardListModel uiModel;
    protected JLabel lblOnetimeShips;
    protected JList<RosterCard> uiList;

    /**
     * Creates One-Time Ship presentation with explicit lookup and artwork dependencies.
     *
     * @param admiral selected Admiral, or {@code null} until the root propagates it
     * @param gameData reference data used by One-Time Ship selection
     * @param iconRenderer renderer used by lists and selection dialogs
     * @throws NullPointerException if {@code gameData} or {@code iconRenderer} is {@code null}
     */
    public OneTimeShipPanel(Admiral admiral, GameData gameData, ShipIconFactory iconRenderer) {
        this(gameData, iconRenderer);
        setAdmiral(admiral);
    }

    /**
     * Builds One-Time Ship controls after capturing the dependencies used by their
     * actions and renderer.
     *
     * @param gameData reference data used by One-Time Ship selection
     * @param iconRenderer renderer used by lists and selection dialogs
     * @throws NullPointerException if either dependency is {@code null}
     */
    private OneTimeShipPanel(GameData gameData, ShipIconFactory iconRenderer) {
        this.gameData = Objects.requireNonNull(gameData, "gameData");
        this.iconRenderer = Objects.requireNonNull(iconRenderer, "iconRenderer");
        GridBagLayout gbl_panel = new GridBagLayout();
        gbl_panel.columnWidths = new int[] { 0, 0, 0 };
        gbl_panel.rowHeights = new int[] { 0, 0, 0, 0, 0 };
        gbl_panel.columnWeights = new double[] { 1.0, 1.0, Double.MIN_VALUE };
        gbl_panel.rowWeights = new double[] { 0.0, 1.0, 0.0, 1.0, Double.MIN_VALUE };
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

    @Override
    public Admiral getAdmiral() {
        return admiral;
    }

    /**
     * Selects the Admiral whose immutable One-Time card view drives this panel.
     * Listener ownership follows the selected Admiral so quantities cannot
     * accumulate across selections.
     *
     * @param admiral selected Admiral, or {@code null} to clear the list
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
     * Refreshes the One-Time quantity from the post-commit Roster view delivered by
     * Admiral.
     *
     * @param change committed Roster transition
     */
    @Override
    public void rosterChanged(RosterChange change) {
        refreshRoster(change.getAfter());
    }

    /**
     * Applies one immutable list of identity-bearing One-Time copies and its
     * quantity label.
     *
     * @param roster complete Roster view, or {@code null} to clear the panel
     */
    private void refreshRoster(RosterView roster) {
        List<RosterCard> cards = roster == null ? List.of() : roster.getOneTimeCards();
        uiModel.setCards(cards);
        lblOnetimeShips.setText(String.format(HtmlOneTimeShips, cards.size()));
    }

    private class AddOneTimeShipAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = -9000567166027604196L;

        public AddOneTimeShipAction() {
            super(LabelShip, Images.ICON_ADD);
            putValue(SHORT_DESCRIPTION, DescAddOneTimeShips);
        }

        /** Increments every selected One-Time Ship type in one Admiral operation. */
        @Override
        public void actionPerformed(ActionEvent e) {
            Window window = SwingUtilities.getWindowAncestor((Component) e.getSource());
            List<Ship> ships = ShipSelectionPanel.dialogAddOneTimeShips(
                    window,
                    admiral.getFaction(),
                    gameData.ships(),
                    iconRenderer,
                    TitleAddOneTimeShips);
            if (!ships.isEmpty()) {
                admiral.adjustOneTimeShipQuantities(ships, 1);
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

        /** Decrements every selected One-Time Ship type in one Admiral operation. */
        @Override
        public void actionPerformed(ActionEvent e) {
            Window window = SwingUtilities.getWindowAncestor((Component) e.getSource());
            RosterView roster = admiral.getRoster();
            List<RosterCard> cards = ShipListPanel.dialogRosterCards(
                    window,
                    RosterCardSelections.oneTimeShipTypes(roster),
                    iconRenderer,
                    TitleRemoveOneTimeShips);
            if (!cards.isEmpty()) {
                admiral.adjustOneTimeShipQuantities(RosterCardSelections.ships(cards), -1);
            }
        }
    }

}
