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
package com.kor.admiralty.ui;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Characterizes how established modal selection dialogs translate Swing
 * outcomes into caller-visible selections.
 */
class DialogSelectionsTest {

    /**
     * Verifies acceptance returns the selected entries in the order supplied by
     * the visible list.
     */
    @Test
    void acceptanceReturnsTheVisibleSelection() {
        List<String> selected = List.of("first", "third");

        List<String> outcome = DialogSelections.forOption(JOptionPane.OK_OPTION, selected);

        assertEquals(List.of("first", "third"), outcome);
    }

    /**
     * Verifies explicit cancellation cannot leak an existing list selection to
     * the caller.
     */
    @Test
    void cancellationReturnsNoSelection() {
        assertEquals(
                List.of(),
                DialogSelections.forOption(JOptionPane.CANCEL_OPTION, List.of("selected")));
    }

    /**
     * Verifies closing the window has the same no-selection result as explicit
     * cancellation.
     */
    @Test
    void windowCloseReturnsNoSelection() {
        assertEquals(
                List.of(),
                DialogSelections.forOption(JOptionPane.CLOSED_OPTION, List.of("selected")));
    }

    /**
     * Verifies accepting with no selected entries remains a safe no-op.
     */
    @Test
    void emptyAcceptanceReturnsNoSelection() {
        assertEquals(
                List.of(),
                DialogSelections.forOption(JOptionPane.OK_OPTION, List.of()));
    }
}
