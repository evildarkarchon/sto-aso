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
package com.kor.admiralty.ui.renderers;

import java.awt.Component;
import java.util.Objects;

import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.ui.resources.Swing;
import com.kor.admiralty.ui.resources.Images;
import com.kor.admiralty.ui.resources.ShipIconFactory;

import java.awt.GridBagLayout;
import javax.swing.JLabel;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Font;
import java.awt.Insets;
import java.io.Serial;

public abstract class BasicShipCellRenderer extends JPanel implements ListCellRenderer<Ship> {

    @Serial
    private static final long serialVersionUID = 3185543403004009734L;

    private final JLabel lblIcon;
    private final JLabel lblName;
    private final ShipIconFactory iconRenderer;

    public BasicShipCellRenderer() {
        this(Images::getIcon);
    }

    /**
     * Creates a Ship renderer with caller-supplied icon presentation.
     *
     * @param iconRenderer renderer for composed Ship artwork
     * @throws NullPointerException if {@code iconRenderer} is {@code null}
     */
    protected BasicShipCellRenderer(ShipIconFactory iconRenderer) {
        this.iconRenderer = Objects.requireNonNull(iconRenderer, "iconRenderer");
        setBorder(Swing.BorderDefault);
        setBackground(Swing.ColorBackground);
        GridBagLayout gridBagLayout = new GridBagLayout();
        gridBagLayout.columnWidths = new int[] { 0, 0 };
        gridBagLayout.rowHeights = new int[] { 0, 0, 0, 0 };
        gridBagLayout.columnWeights = new double[] { 0.0, 0.0 };
        gridBagLayout.rowWeights = new double[] { 0.0, 0.0, 0.0, Double.MIN_VALUE };
        setLayout(gridBagLayout);

        Dimension dim64 = new Dimension(64, 64);

        lblIcon = new JLabel();
        lblIcon.setPreferredSize(dim64);
        GridBagConstraints gbc_lblIcon = new GridBagConstraints();
        gbc_lblIcon.anchor = GridBagConstraints.NORTHWEST;
        gbc_lblIcon.gridheight = 3;
        gbc_lblIcon.insets = new Insets(5, 5, 0, 5);
        gbc_lblIcon.gridx = 0;
        gbc_lblIcon.gridy = 0;
        add(lblIcon, gbc_lblIcon);

        lblName = new JLabel("Ship Name");
        lblName.setFont(new Font("Tahoma", Font.BOLD, 12));
        GridBagConstraints gbc_lblName = new GridBagConstraints();
        gbc_lblName.fill = GridBagConstraints.HORIZONTAL;
        gbc_lblName.weightx = 10.0;
        gbc_lblName.insets = new Insets(5, 0, 5, 5);
        gbc_lblName.gridx = 1;
        gbc_lblName.gridy = 0;
        add(lblName, gbc_lblName);
    }

    public void setShip(Ship ship) {
        getListCellRendererComponent(null, ship, 0, true, false);
    }

    /**
     * Renders a generic canonical Ship without inferring per-Admiral state from
     * shared reference data.
     *
     * @param list         owning Swing list
     * @param ship         canonical Ship facts, or null for an empty cell
     * @param index        visible list index
     * @param isSelected   whether Swing selected the cell
     * @param cellHasFocus whether the cell owns focus
     * @return configured renderer component
     */
    @Override
    public Component getListCellRendererComponent(JList<? extends Ship> list, Ship ship, int index, boolean isSelected,
            boolean cellHasFocus) {
        return renderShip(ship, false, isSelected);
    }

    /**
     * Renders canonical Ship facts with caller-supplied Roster presentation state.
     * Ship Statistics supplies projected membership so icon selection never reads
     * shared Ship state.
     *
     * @param ship                  canonical Ship facts, or null for the empty cell
     * @param useRosterPresentation whether the icon should use the current-Roster
     *                              presentation
     * @param isSelected            whether Swing selected this cell
     * @return this configured renderer component
     */
    protected Component renderShip(Ship ship, boolean useRosterPresentation, boolean isSelected) {
        return renderShip(
                ship,
                ship == null ? null : ship.getDisplayName(),
                useRosterPresentation,
                isSelected);
    }

    /**
     * Renders canonical Ship facts with explicit display text and caller-supplied
     * Roster presentation state.
     * Roster cards use this overload so One-Time presentation does not require a
     * Ship-shaped adapter.
     *
     * @param ship                  canonical Ship facts, or null for the empty cell
     * @param displayName           presentation name supplied by the owning
     *                              immutable view
     * @param useRosterPresentation whether the icon should use the current-Roster
     *                              presentation
     * @param isSelected            whether Swing selected this cell
     * @return this configured renderer component
     */
    protected Component renderShip(
            Ship ship,
            String displayName,
            boolean useRosterPresentation,
            boolean isSelected) {
        if (ship == null) {
            lblIcon.setIcon(Images.ICON_BLANK);
            lblName.setText("No Ship");
            lblName.setForeground(Rarity.Common.getColor());
        } else {
            lblIcon.setIcon(iconRenderer.getIcon(
                    ship.getIconName(),
                    ship.getFaction(),
                    ship.getRole(),
                    ship.getRarity(),
                    useRosterPresentation));
            lblName.setText(displayName);
            lblName.setForeground(ship.getRarity().getColor());
        }
        if (isSelected) {
            setBorder(Swing.BorderHighlighted);
            setBackground(Swing.ColorBackgroundHighlighted);
        } else {
            setBorder(Swing.BorderDefault);
            setBackground(Swing.ColorBackground);
        }
        return this;
    }

}
