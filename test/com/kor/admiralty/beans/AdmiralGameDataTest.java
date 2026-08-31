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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.kor.admiralty.beans;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.kor.admiralty.io.GameData;

/**
 * Specifies construction-safe GameData ownership through the public Admirals
 * seam.
 */
class AdmiralGameDataTest {

    /**
     * Verifies Admirals owns creation of default and newly added Admirals whose
     * empty Rosters are valid immediately.
     */
    @Test
    void admiralsCreatesLookupReadyEmptyRosters() {
        GameData gameData = GameData.builder().build();
        Admirals admirals = new Admirals(gameData);
        Admiral defaultAdmiral = admirals.getAdmirals().getFirst();

        Admiral addedAdmiral = admirals.addAdmiral();

        assertAll(
                () -> assertTrue(defaultAdmiral.getRoster().getActiveCards().isEmpty()),
                () -> assertTrue(defaultAdmiral.getRoster().getMaintenanceCards().isEmpty()),
                () -> assertTrue(defaultAdmiral.getRoster().getOneTimeCards().isEmpty()),
                () -> assertTrue(defaultAdmiral.getUsageCounts().isEmpty()),
                () -> assertTrue(addedAdmiral.getRoster().getActiveCards().isEmpty()),
                () -> assertTrue(addedAdmiral.getRoster().getMaintenanceCards().isEmpty()),
                () -> assertTrue(addedAdmiral.getRoster().getOneTimeCards().isEmpty()),
                () -> assertTrue(addedAdmiral.getUsageCounts().isEmpty()));
    }
}
