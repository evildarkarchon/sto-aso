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

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.io.Serial;
import java.util.List;
import java.util.Objects;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.RosterView;
import com.kor.admiralty.ui.components.JColumnList;
import com.kor.admiralty.ui.components.JListComponentAdapter;
import com.kor.admiralty.ui.models.RosterCardListModel;
import com.kor.admiralty.ui.renderers.RosterCardCellRenderer;
import com.kor.admiralty.ui.resources.ShipIconFactory;
import com.kor.admiralty.ui.resources.Swing;

import java.awt.GridBagLayout;
import javax.swing.ScrollPaneConstants;

public class StarshipTraitsPanel extends JPanel {

    @Serial
    private static final long serialVersionUID = -8042884852436619063L;

    protected RosterView rosterView;
    protected RosterCardListModel uiModel;
    protected JList<RosterCard> uiList;

    /**
     * Builds Starship Trait controls that render only root-supplied Roster
     * projections.
     *
     * @param iconRenderer renderer used by trait-bearing Roster cards
     * @throws NullPointerException if {@code iconRenderer} is {@code null}
     */
    StarshipTraitsPanel(ShipIconFactory iconRenderer) {
        Objects.requireNonNull(iconRenderer, "iconRenderer");
        GridBagLayout gbl_panel = new GridBagLayout();
        gbl_panel.columnWidths = new int[]{0};
        gbl_panel.rowHeights = new int[]{0, 0, 0};
        gbl_panel.columnWeights = new double[]{0.0};
        gbl_panel.rowWeights = new double[]{0.0, 0.0, Double.MIN_VALUE};
        setLayout(gbl_panel);

        JLabel label = new JLabel("Unlockable Starship Traits:");
        label.setForeground(Color.BLACK);
        GridBagConstraints gbc_label = new GridBagConstraints();
        gbc_label.weightx = 1.0;
        gbc_label.fill = GridBagConstraints.BOTH;
        gbc_label.insets = new Insets(5, 5, 5, 5);
        gbc_label.gridx = 0;
        gbc_label.gridy = 0;
        add(label, gbc_label);

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        GridBagConstraints gbc_scrollPane = new GridBagConstraints();
        gbc_scrollPane.weighty = 1.0;
        gbc_scrollPane.weightx = 1.0;
        gbc_scrollPane.fill = GridBagConstraints.BOTH;
        gbc_scrollPane.insets = new Insets(0, 5, 5, 5);
        gbc_scrollPane.gridx = 0;
        gbc_scrollPane.gridy = 1;
        add(scrollPane, gbc_scrollPane);

        uiModel = new RosterCardListModel();
        uiList = new JColumnList<RosterCard>(uiModel);
        uiList.setLayoutOrientation(JList.VERTICAL);
        uiList.setCellRenderer(RosterCardCellRenderer.starshipTraitCards(iconRenderer));
        scrollPane.setViewportView(uiList);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        addComponentListener(new JListComponentAdapter<RosterCard>(uiList));
    }

    /**
     * Projects trait-bearing reusable cards from one coherent immutable Roster
     * revision supplied by the workspace root.
     *
     * @param view complete immutable workspace projection
     * @throws NullPointerException  if {@code view} is {@code null}
     * @throws IllegalStateException if called outside the Swing event thread
     */
    void render(AdmiralWorkspaceView view) {
        Swing.requireEventDispatchThread("project Starship Trait state");
        Objects.requireNonNull(view, "view");
        rosterView = view.roster();
        List<RosterCard> cards = rosterView.getReusableCards().stream()
                .filter(card -> card.getShip().hasTrait())
                .toList();
        uiModel.setCards(cards);
    }

}
