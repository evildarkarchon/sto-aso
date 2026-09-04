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

import java.util.Collections;
import java.util.List;

import javax.swing.JOptionPane;

/**
 * Preserves the established modal outcome contract shared by Ship and
 * Roster-card selection dialogs.
 */
final class DialogSelections {

    private DialogSelections() {
    }

    /**
     * Returns visible selections only for explicit acceptance; cancellation and
     * window close both become an empty result.
     *
     * @param <E>             selected entry type
     * @param option          result returned by the Swing option dialog
     * @param selectedEntries selected entries in visible-list order
     * @return the supplied selection for acceptance, otherwise an empty list
     */
    static <E> List<E> forOption(int option, List<E> selectedEntries) {
        if (option == JOptionPane.OK_OPTION) {
            return selectedEntries;
        }
        return Collections.emptyList();
    }
}
