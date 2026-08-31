/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty.ui.panels;

import java.util.List;
import java.util.Objects;

import com.kor.admiralty.beans.AssignmentView;
import com.kor.admiralty.beans.RosterView;
import com.kor.admiralty.enums.PlayerFaction;

/**
 * Immutable, internally coherent display projection for one fixed Admiral
 * workspace revision.
 */
record AdmiralWorkspaceView(
        String name,
        PlayerFaction faction,
        boolean prioritizeActive,
        int assignmentCount,
        List<AssignmentView> assignments,
        RosterView roster) {

    /**
     * Validates reference values and snapshots the Assignment projection list.
     *
     * @throws NullPointerException if a reference or Assignment element is null
     */
    AdmiralWorkspaceView {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(faction, "faction");
        assignments = List.copyOf(assignments);
        Objects.requireNonNull(roster, "roster");
    }
}
