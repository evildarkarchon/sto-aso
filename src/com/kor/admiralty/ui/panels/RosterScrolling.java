/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.kor.admiralty.ui.panels;

import javax.swing.*;
import java.awt.event.AdjustmentEvent;

/**
 * Applies the established card-sized scrolling policy to Roster lists.
 */
final class RosterScrolling {

    private static final int CARD_INCREMENT = 76;

    private RosterScrolling() {
    }

    /**
     * Configures Active or Maintenance scrolling with a fixed card-sized wheel
     * unit and the shared tracked-block behavior.
     *
     * @param scrollPane reusable-Ship list container
     */
    static void configureReusableCards(JScrollPane scrollPane) {
        scrollPane.setWheelScrollingEnabled(true);
        scrollPane.getVerticalScrollBar().setUnitIncrement(CARD_INCREMENT);
        configureTrackedBlockIncrement(scrollPane.getVerticalScrollBar());
    }

    /**
     * Configures One-Time scrolling while retaining its natural JList row unit.
     *
     * @param scrollPane One-Time Ship list container
     */
    static void configureOneTimeCards(JScrollPane scrollPane) {
        scrollPane.setWheelScrollingEnabled(true);
        configureTrackedBlockIncrement(scrollPane.getVerticalScrollBar());
    }

    /**
     * Restores the legacy tracked adjustment that advances by one rendered card.
     *
     * @param scrollBar Roster list's vertical scrollbar
     */
    private static void configureTrackedBlockIncrement(JScrollBar scrollBar) {
        scrollBar.addAdjustmentListener(event -> {
            if (event.getAdjustmentType() == AdjustmentEvent.TRACK) {
                // Look-and-feel tracking can recompute the block size, so pin it when the
                // adjustment occurs rather than only during construction.
                event.getAdjustable().setBlockIncrement(CARD_INCREMENT);
            }
        });
    }
}
