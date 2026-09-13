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

import com.kor.admiralty.beans.ShipUsageRow;
import com.kor.admiralty.ui.resources.ShipIconFactory;
import com.kor.admiralty.ui.resources.Swing;

import javax.swing.*;
import java.awt.*;
import java.io.Serial;

/**
 * Renders immutable Ship Statistics rows without reading deployment counts or
 * Roster membership from canonical Ships.
 */
public class UsageCountCellRenderer extends JPanel implements ListCellRenderer<ShipUsageRow> {

    @Serial
    private static final long serialVersionUID = 8576217946824150570L;

    protected final ShipCellRenderer shipRenderer;
    protected final JLabel lblUsageCount;

    /**
     * Creates a row renderer that presents canonical Ship facts beside the
     * projected deployment count.
     *
     * @param iconRenderer renderer for composed Ship artwork
     * @throws NullPointerException if {@code iconRenderer} is {@code null}
     */
    public UsageCountCellRenderer(ShipIconFactory iconRenderer) {
        super(new BorderLayout());
        shipRenderer = new ShipCellRenderer(iconRenderer);
        add(shipRenderer, BorderLayout.CENTER);

        Dimension dim64 = new Dimension(64, 64);
        lblUsageCount = new JLabel("0");
        lblUsageCount.setHorizontalAlignment(SwingConstants.CENTER);
        lblUsageCount.setForeground(Color.WHITE);
        lblUsageCount.setFont(new Font("Tahoma", Font.BOLD, 16));
        lblUsageCount.setPreferredSize(dim64);
        lblUsageCount.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 0, 5, 5));
        add(lblUsageCount, BorderLayout.EAST);
        renderRow(null, true);
    }

    /**
     * Configures the component outside a JList, primarily for embedded preview
     * panels.
     *
     * @param row immutable usage row, or null for the empty presentation
     */
    public void setUsageRow(ShipUsageRow row) {
        renderRow(row, true);
    }

    @Override
    public Component getListCellRendererComponent(
            JList<? extends ShipUsageRow> list,
            ShipUsageRow row,
            int index,
            boolean isSelected,
            boolean cellHasFocus) {
        renderRow(row, isSelected);
        return this;
    }

    /**
     * Applies one immutable usage snapshot to both canonical Ship and
     * aggregate-count presentation.
     *
     * @param row        projected usage row, or null for an empty cell
     * @param isSelected whether Swing selected this cell
     */
    private void renderRow(ShipUsageRow row, boolean isSelected) {
        shipRenderer.renderShip(
                row == null ? null : row.ship(),
                row != null && row.inCurrentRoster(),
                isSelected);
        // The outer row owns the single selection border surrounding both Ship facts
        // and its deployment count.
        shipRenderer.setBorder(null);
        lblUsageCount.setText(String.format("%,d", row == null ? 0 : row.deploymentCount()));
        setBorder(isSelected ? Swing.BorderHighlighted : Swing.BorderDefault);
        setBackground(isSelected ? Swing.ColorBackgroundHighlighted : Swing.ColorBackground);
    }
}
