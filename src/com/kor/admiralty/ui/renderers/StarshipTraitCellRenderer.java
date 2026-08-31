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

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.io.Serial;

import javax.swing.JTextPane;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

import com.kor.admiralty.beans.Ship;

public class StarshipTraitCellRenderer extends BasicShipCellRenderer {

    @Serial
    private static final long serialVersionUID = -8211496775487348321L;
    private static final StyleSheet STYLESHEET_GLOBAL = new HTMLEditorKit().getStyleSheet();
    private static final String STARSHIP_TRAIT_CSS = "h1{color:white;font-size:110%;font-weight:bold;font-style:italic;line-height:1.0;margin-bottom:0em}"
            +
            "h2{color:white;font-size:100%;font-weight:bold;line-height:1.0;margin-bottom:0em}" +
            "p{color:white}" +
            "ul{color:white}";
    private static final CustomHTMLEditorKit HTML_KIT = new CustomHTMLEditorKit(customStyleSheet(STARSHIP_TRAIT_CSS));

    protected JTextPane lblStarshipTrait;

    /**
     * Extends Swing's default HTML rules with the Starship Trait presentation used
     * by this renderer.
     *
     * @param css renderer-owned CSS rules
     * @return a stylesheet retaining Swing defaults plus the supplied rules
     */
    private static StyleSheet customStyleSheet(String css) {
        StyleSheet stylesheet = new StyleSheet();
        stylesheet.addStyleSheet(STYLESHEET_GLOBAL);
        stylesheet.addRule(css);
        return stylesheet;
    }

    /**
     * Create the panel.
     */
    public StarshipTraitCellRenderer() {
        super();

        lblStarshipTrait = new JTextPane();
        lblStarshipTrait.setContentType("text/html");
        lblStarshipTrait.setEditorKit(HTML_KIT);
        lblStarshipTrait.setEditable(false);
        lblStarshipTrait.setOpaque(false);
        lblStarshipTrait.setBackground(new Color(0, 0, 0, 0));
        lblStarshipTrait.setDocument(HTML_KIT.createDefaultDocument());
        GridBagConstraints gbc_lblStarshipTrait = new GridBagConstraints();
        gbc_lblStarshipTrait.fill = GridBagConstraints.BOTH;
        gbc_lblStarshipTrait.anchor = GridBagConstraints.WEST;
        gbc_lblStarshipTrait.weightx = 10.0;
        gbc_lblStarshipTrait.gridheight = 2;
        gbc_lblStarshipTrait.insets = new Insets(0, 0, 0, 5);
        gbc_lblStarshipTrait.gridx = 1;
        gbc_lblStarshipTrait.gridy = 1;
        add(lblStarshipTrait, gbc_lblStarshipTrait);

        setShip(null);
    }

    public void setShip(Ship ship) {
        getListCellRendererComponent(null, ship, 0, true, false);
    }

    /**
     * Renders canonical Ship facts and the resolved Starship Trait description.
     *
     * @param ship                  canonical Ship facts, or null for an empty cell
     * @param displayName           text selected by the owning projection
     * @param useRosterPresentation whether to use owned/actual artwork
     * @param isSelected            whether Swing selected the cell
     * @return this configured renderer component
     */
    @Override
    protected Component renderShip(
            Ship ship,
            String displayName,
            boolean useRosterPresentation,
            boolean isSelected) {
        super.renderShip(ship, displayName, useRosterPresentation, isSelected);
        if (ship == null) {
            lblStarshipTrait.setText("");
        } else {
            lblStarshipTrait.setText(ship.getTrait());
        }
        return this;
    }

}
